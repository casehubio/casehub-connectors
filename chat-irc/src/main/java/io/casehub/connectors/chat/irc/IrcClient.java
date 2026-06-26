package io.casehub.connectors.chat.irc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.chat.irc.protocol.ChannelInfo;
import io.casehub.connectors.chat.irc.protocol.IrcCommand;
import io.casehub.connectors.chat.irc.protocol.IrcMessage;
import io.casehub.connectors.chat.irc.protocol.IrcParser;

/**
 * Raw TCP IRC client. Connects, sends commands, reads responses, maintains read loop.
 * PURE TRANSPORT — does NOT reconnect or backoff. On IOException in read loop, sets
 * isConnected() = false and exits. Reconnection is the caller's responsibility.
 */
@ApplicationScoped
public class IrcClient {

    private final String host;
    private final int port;
    private final String nick;

    private volatile boolean connected = false;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread readThread;
    private final ReentrantLock writeLock = new ReentrantLock();

    private Consumer<IrcMessage> messageCallback;

    // Collectors for request/response coordination
    private final ConcurrentHashMap<String, CompletableFuture<Void>> joinCollectors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NamesCollector> namesCollectors = new ConcurrentHashMap<>();
    private volatile ListCollector listCollector;

    /**
     * Constructor for both CDI (with config properties injected) and tests (with explicit values).
     * When used by CDI, ArC injects the config properties. When used by tests, explicit values are passed.
     */
    @Inject
    public IrcClient(
            @ConfigProperty(name = "casehub.connectors.chat-irc.host") String host,
            @ConfigProperty(name = "casehub.connectors.chat-irc.port", defaultValue = "6667") int port,
            @ConfigProperty(name = "casehub.connectors.chat-irc.nick") String nick) {
        this.host = host;
        this.port = port;
        this.nick = nick;
    }

    public boolean connect() {
        if (connected) {
            return true;
        }

        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            // Start read thread BEFORE sending handshake
            readThread = new Thread(this::readLoop, "IRC-Read-" + nick);
            readThread.setDaemon(true);
            readThread.start();

            // Register collector for 001 welcome
            CompletableFuture<Void> welcomeFuture = new CompletableFuture<>();
            String welcomeKey = IrcCommand.RPL_WELCOME;
            joinCollectors.put(welcomeKey, welcomeFuture);

            // Send NICK and USER
            writeLine(IrcParser.format("NICK", nick));
            writeLine(IrcParser.format("USER", nick, "0", "*", nick));

            // Wait for 001 reply (10s timeout)
            try {
                welcomeFuture.get(10, TimeUnit.SECONDS);
                connected = true;
                return true;
            } catch (Exception e) {
                cleanup();
                return false;
            } finally {
                joinCollectors.remove(welcomeKey);
            }
        } catch (IOException e) {
            return false;
        }
    }

    public void disconnect() {
        if (!connected) {
            return;
        }

        try {
            writeLine("QUIT");
        } catch (Exception e) {
            // ignore
        } finally {
            connected = false;
            cleanup();

            if (readThread != null) {
                readThread.interrupt();
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean join(String channel) {
        if (!connected) {
            return false;
        }

        // Register collector for 366 (end of names) for this channel
        String key = IrcCommand.RPL_ENDOFNAMES + ":" + channel;
        CompletableFuture<Void> future = new CompletableFuture<>();
        joinCollectors.put(key, future);

        try {
            writeLine(IrcParser.format("JOIN", channel));
            future.get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            joinCollectors.remove(key);
        }
    }

    public void part(String channel) {
        if (connected) {
            writeLine(IrcParser.format("PART", channel));
        }
    }

    public boolean send(String channel, String message) {
        if (!connected) {
            return false;
        }

        writeLine(IrcParser.format("PRIVMSG", channel, message));
        return true;
    }

    public List<ChannelInfo> listChannels() {
        if (!connected) {
            return List.of();
        }

        ListCollector collector = new ListCollector();
        listCollector = collector;

        try {
            writeLine("LIST");
            collector.future.get(10, TimeUnit.SECONDS);
            return collector.channels;
        } catch (Exception e) {
            return List.of();
        } finally {
            listCollector = null;
        }
    }

    public List<String> names(String channel) {
        if (!connected) {
            return List.of();
        }

        NamesCollector collector = new NamesCollector();
        String key = IrcCommand.RPL_ENDOFNAMES + ":" + channel;
        namesCollectors.put(key, collector);

        try {
            writeLine(IrcParser.format("NAMES", channel));
            collector.future.get(10, TimeUnit.SECONDS);
            return collector.nicks;
        } catch (Exception e) {
            return List.of();
        } finally {
            namesCollectors.remove(key);
        }
    }

    public void setMessageCallback(Consumer<IrcMessage> callback) {
        this.messageCallback = callback;
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                IrcMessage message = IrcParser.parse(line);
                handleMessage(message);

                // If disconnect was called, exit
                if (!connected && socket.isClosed()) {
                    break;
                }
            }
        } catch (IOException e) {
            // Connection lost
        } finally {
            connected = false;
            // Complete all pending futures exceptionally so waiting threads don't hang
            failAllCollectors();
        }
    }

    private void failAllCollectors() {
        IOException connectionLost = new IOException("connection lost");

        // Fail all join collectors
        for (CompletableFuture<Void> future : joinCollectors.values()) {
            if (!future.isDone()) {
                future.completeExceptionally(connectionLost);
            }
        }
        joinCollectors.clear();

        // Fail all names collectors
        for (NamesCollector collector : namesCollectors.values()) {
            if (!collector.future.isDone()) {
                collector.future.completeExceptionally(connectionLost);
            }
        }
        namesCollectors.clear();

        // Fail list collector
        ListCollector collector = listCollector;
        if (collector != null && !collector.future.isDone()) {
            collector.future.completeExceptionally(connectionLost);
        }
        listCollector = null;
    }

    private void handleMessage(IrcMessage message) {
        String command = message.command();

        // Handle PING
        if ("PING".equals(command)) {
            if (!message.params().isEmpty()) {
                writeLine(IrcParser.format("PONG", message.params().get(0)));
            }
            return;
        }

        // Handle PRIVMSG
        if ("PRIVMSG".equals(command)) {
            if (messageCallback != null) {
                messageCallback.accept(message);
            }
            return;
        }

        // Handle 001 (welcome)
        if (IrcCommand.RPL_WELCOME.equals(command)) {
            CompletableFuture<Void> future = joinCollectors.get(IrcCommand.RPL_WELCOME);
            if (future != null) {
                future.complete(null);
            }
            return;
        }

        // Handle 322 (LIST reply)
        if (IrcCommand.RPL_LIST.equals(command)) {
            ListCollector collector = listCollector;
            if (collector != null && message.params().size() >= 3) {
                String channel = message.params().get(1);
                int memberCount = 0;
                try {
                    memberCount = Integer.parseInt(message.params().get(2));
                } catch (NumberFormatException e) {
                    // ignore
                }
                String topic = message.params().size() > 3 ? message.params().get(3) : "";
                collector.channels.add(new ChannelInfo(channel, memberCount, topic));
            }
            return;
        }

        // Handle 323 (LIST end)
        if (IrcCommand.RPL_LISTEND.equals(command)) {
            ListCollector collector = listCollector;
            if (collector != null) {
                collector.future.complete(null);
            }
            return;
        }

        // Handle 353 (NAMES reply)
        if (IrcCommand.RPL_NAMREPLY.equals(command)) {
            if (message.params().size() >= 4) {
                String channel = message.params().get(2);
                String nicks = message.params().get(3);
                String key = IrcCommand.RPL_ENDOFNAMES + ":" + channel;
                NamesCollector collector = namesCollectors.get(key);
                if (collector != null) {
                    String[] nickArray = nicks.split(" ");
                    for (String n : nickArray) {
                        collector.nicks.add(n);
                    }
                }
            }
            return;
        }

        // Handle 366 (NAMES end / JOIN end)
        if (IrcCommand.RPL_ENDOFNAMES.equals(command)) {
            if (message.params().size() >= 2) {
                String channel = message.params().get(1);
                String key = IrcCommand.RPL_ENDOFNAMES + ":" + channel;

                // Complete join future
                CompletableFuture<Void> joinFuture = joinCollectors.get(key);
                if (joinFuture != null) {
                    joinFuture.complete(null);
                }

                // Complete names future
                NamesCollector namesCollector = namesCollectors.get(key);
                if (namesCollector != null) {
                    namesCollector.future.complete(null);
                }
            }
        }
    }

    private void writeLine(String line) {
        writeLock.lock();
        try {
            if (writer != null) {
                writer.println(line);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void cleanup() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            // ignore
        }

        if (writer != null) {
            writer.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private static class ListCollector {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final List<ChannelInfo> channels = new ArrayList<>();
    }

    private static class NamesCollector {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final List<String> nicks = new ArrayList<>();
    }
}
