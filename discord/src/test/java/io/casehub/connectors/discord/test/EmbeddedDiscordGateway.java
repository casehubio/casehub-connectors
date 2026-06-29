package io.casehub.connectors.discord.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal WebSocket server simulating Discord Gateway v10 for testing.
 *
 * <p>Uses raw {@link ServerSocket} with manual WebSocket upgrade handshake
 * and frame encoding/decoding. Text frames only, no binary. Follows the
 * {@code EmbeddedIrcServer} pattern: port 0, virtual threads, minimal API.
 */
public final class EmbeddedDiscordGateway {

    // JDK 25+/26+ uses a modified GUID (ending B11) instead of the RFC 6455 standard (ending B63).
    // The embedded server must match what the JDK WebSocket client expects.
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int HEARTBEAT_INTERVAL_MS = 500;

    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private volatile boolean running;
    private volatile boolean suppressAcks;
    private volatile boolean rejectConnections;
    private final CopyOnWriteArrayList<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> receivedIdentifies = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> receivedResumes = new CopyOnWriteArrayList<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private volatile CountDownLatch connectionLatch;
    private final AtomicInteger dispatchSeq = new AtomicInteger(0);

    public synchronized void start() throws IOException {
        serverSocket = new ServerSocket(0);
        running = true;
        acceptorThread = Thread.ofVirtual().name("EmbeddedGateway-accept").start(this::acceptLoop);
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
        for (ClientSession session : sessions) {
            session.close();
        }
        sessions.clear();
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void suppressHeartbeatAcks(boolean suppress) {
        this.suppressAcks = suppress;
    }

    public void rejectConnections(boolean reject) {
        this.rejectConnections = reject;
    }

    /** Sends a DISPATCH (opcode 0) to all connected clients. */
    public void sendDispatch(String eventType, String dataJson) {
        int seq = dispatchSeq.incrementAndGet();
        String payload = "{\"op\":0,\"s\":" + seq + ",\"t\":\"" + eventType + "\",\"d\":" + dataJson + "}";
        for (ClientSession session : sessions) {
            session.sendText(payload);
        }
    }

    /** Sends a DISPATCH split across two WebSocket frames (for multi-frame testing). */
    public void sendDispatchMultiFrame(String eventType, String dataJson) {
        int seq = dispatchSeq.incrementAndGet();
        String payload = "{\"op\":0,\"s\":" + seq + ",\"t\":\"" + eventType + "\",\"d\":" + dataJson + "}";
        int mid = payload.length() / 2;
        String part1 = payload.substring(0, mid);
        String part2 = payload.substring(mid);
        for (ClientSession session : sessions) {
            session.sendFrame(part1, 0x01, false); // text opcode, not final
            session.sendFrame(part2, 0x00, true);  // continuation opcode, final
        }
    }

    /** Sends RECONNECT (opcode 7) to all connected clients. */
    public void sendReconnect() {
        String payload = "{\"op\":7,\"d\":null}";
        for (ClientSession session : sessions) {
            session.sendText(payload);
        }
    }

    /** Sends INVALID_SESSION (opcode 9) to all connected clients. */
    public void sendInvalidSession(boolean resumable) {
        String payload = "{\"op\":9,\"d\":" + resumable + "}";
        for (ClientSession session : sessions) {
            session.sendText(payload);
        }
    }

    /** Forcefully closes all client connections. */
    public void disconnectAllClients() {
        for (ClientSession session : sessions) {
            session.close();
        }
    }

    public java.util.List<String> getReceivedIdentifies() {
        return java.util.List.copyOf(receivedIdentifies);
    }

    public java.util.List<String> getReceivedResumes() {
        return java.util.List.copyOf(receivedResumes);
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }

    /** Sets a latch that counts down each time a new client connects. */
    public void expectConnections(int count) {
        connectionLatch = new CountDownLatch(count);
    }

    /** Waits for the expected number of connections. Returns false on timeout. */
    public boolean awaitConnections(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = connectionLatch;
        return latch != null && latch.await(timeout, unit);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connectionCount.incrementAndGet();

                if (rejectConnections) {
                    socket.close();
                    CountDownLatch latch = connectionLatch;
                    if (latch != null) latch.countDown();
                    continue;
                }

                ClientSession session = new ClientSession(socket);
                sessions.add(session);
                Thread.ofVirtual().name("EmbeddedGateway-client").start(session);

            } catch (IOException e) {
                if (running) {
                    // unexpected
                }
            }
        }
    }

    private class ClientSession implements Runnable {
        private final Socket socket;
        private OutputStream out;
        private InputStream in;
        private volatile boolean active = true;

        ClientSession(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // Perform WebSocket handshake
                if (!doHandshake()) {
                    close();
                    return;
                }

                CountDownLatch latch = connectionLatch;
                if (latch != null) latch.countDown();

                // Send HELLO (opcode 10)
                sendText("{\"op\":10,\"d\":{\"heartbeat_interval\":" + HEARTBEAT_INTERVAL_MS + "}}");

                // Read loop: process client frames
                while (active && running) {
                    String message = readTextFrame();
                    if (message == null) break;
                    handleClientMessage(message);
                }
            } catch (IOException e) {
                // connection closed
            } finally {
                close();
            }
        }

        private boolean doHandshake() throws IOException {
            // Read HTTP upgrade request
            StringBuilder request = new StringBuilder();
            byte[] buf = new byte[1];
            while (true) {
                int n = in.read(buf);
                if (n < 0) return false;
                request.append((char) buf[0]);
                if (request.length() >= 4 && request.substring(request.length() - 4).equals("\r\n\r\n")) {
                    break;
                }
            }

            // Extract Sec-WebSocket-Key
            String key = null;
            for (String line : request.toString().split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                    break;
                }
            }
            if (key == null) return false;

            // Compute accept key
            String acceptKey;
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                acceptKey = Base64.getEncoder().encodeToString(sha1.digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                return false;
            }

            // Send upgrade response
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        }

        private void handleClientMessage(String message) {
            // Parse opcode from JSON
            try {
                int opIndex = message.indexOf("\"op\":");
                if (opIndex < 0) return;
                int colonPos = message.indexOf(':', opIndex + 4);
                int commaPos = message.indexOf(',', colonPos);
                int bracePos = message.indexOf('}', colonPos);
                int end = commaPos >= 0 && (bracePos < 0 || commaPos < bracePos) ? commaPos : bracePos;
                int op = Integer.parseInt(message.substring(colonPos + 1, end).trim());

                switch (op) {
                    case 1: // HEARTBEAT
                        if (!suppressAcks) {
                            sendText("{\"op\":11,\"d\":null}");
                        }
                        break;
                    case 2: // IDENTIFY
                        receivedIdentifies.add(message);
                        // Send READY dispatch
                        int seq = dispatchSeq.incrementAndGet();
                        sendText("{\"op\":0,\"s\":" + seq + ",\"t\":\"READY\",\"d\":{" +
                                "\"session_id\":\"test-session-id\"," +
                                "\"resume_gateway_url\":\"ws://localhost:" + getPort() + "\"" +
                                "}}");
                        break;
                    case 6: // RESUME
                        receivedResumes.add(message);
                        // Resume is acknowledged by replaying missed events — for tests, just continue
                        break;
                }
            } catch (NumberFormatException e) {
                // malformed opcode — ignore
            }
        }

        /** Reads a complete WebSocket text message, unmasking client frames. Returns null on close/error. */
        String readTextFrame() throws IOException {
            StringBuilder accumulated = new StringBuilder();

            while (true) {
                int b0 = in.read();
                if (b0 < 0) return null;
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;

                // Close frame
                if (opcode == 0x08) return null;

                int b1 = in.read();
                if (b1 < 0) return null;
                boolean masked = (b1 & 0x80) != 0;
                long payloadLen = b1 & 0x7F;

                if (payloadLen == 126) {
                    int hi = in.read();
                    int lo = in.read();
                    if (hi < 0 || lo < 0) return null;
                    payloadLen = (hi << 8) | lo;
                } else if (payloadLen == 127) {
                    payloadLen = 0;
                    for (int i = 0; i < 8; i++) {
                        int b = in.read();
                        if (b < 0) return null;
                        payloadLen = (payloadLen << 8) | b;
                    }
                }

                byte[] maskKey = null;
                if (masked) {
                    maskKey = new byte[4];
                    if (in.readNBytes(maskKey, 0, 4) < 4) return null;
                }

                byte[] payload = in.readNBytes((int) payloadLen);
                if (payload.length < payloadLen) return null;

                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= maskKey[i % 4];
                    }
                }

                accumulated.append(new String(payload, StandardCharsets.UTF_8));
                if (fin) break;
            }

            return accumulated.toString();
        }

        /** Sends a complete (FIN=1) text frame. */
        void sendText(String text) {
            sendFrame(text, 0x01, true);
        }

        /**
         * Sends a WebSocket frame with explicit opcode and FIN control.
         * Use opcode 0x01 for the first (or only) text frame, 0x00 for continuation frames.
         */
        synchronized void sendFrame(String text, int opcode, boolean fin) {
            if (!active) return;
            try {
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                int b0 = (fin ? 0x80 : 0x00) | opcode;
                if (payload.length <= 125) {
                    out.write(new byte[]{(byte) b0, (byte) payload.length});
                } else if (payload.length <= 65535) {
                    out.write(new byte[]{(byte) b0, 126,
                            (byte) (payload.length >> 8), (byte) (payload.length & 0xFF)});
                } else {
                    byte[] header = new byte[10];
                    header[0] = (byte) b0;
                    header[1] = 127;
                    for (int i = 0; i < 8; i++) {
                        header[9 - i] = (byte) (payload.length >> (8 * i));
                    }
                    out.write(header);
                }
                out.write(payload);
                out.flush();
            } catch (IOException e) {
                // client disconnected
                active = false;
            }
        }

        void close() {
            active = false;
            sessions.remove(this);
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
