package io.casehub.connectors.chat.irc.test;

import io.casehub.connectors.chat.irc.protocol.IrcCommand;
import io.casehub.connectors.chat.irc.protocol.IrcMessage;
import io.casehub.connectors.chat.irc.protocol.IrcParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public final class EmbeddedIrcServer {

    private final int requestedPort;
    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, Set<ClientHandler>> channelMembers = new ConcurrentHashMap<>();
    private final Map<String, String> channelTopics = new ConcurrentHashMap<>();
    private final List<ReceivedPrivmsg> receivedMessages = new CopyOnWriteArrayList<>();

    public record ReceivedPrivmsg(String from, String channel, String text) {}

    public EmbeddedIrcServer(final int port) {
        this.requestedPort = port;
    }

    public synchronized void start() {
        try {
            serverSocket = new ServerSocket(requestedPort);
            acceptorThread = Thread.ofVirtual().start(this::acceptLoop);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start IRC server", e);
        }
    }

    public synchronized void stop() {
        try {
            if (acceptorThread != null) {
                acceptorThread.interrupt();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to stop IRC server", e);
        }
    }

    public int getPort() {
        if (serverSocket == null) {
            throw new IllegalStateException("Server not started");
        }
        return serverSocket.getLocalPort();
    }

    public List<ReceivedPrivmsg> getReceivedMessages() {
        return List.copyOf(receivedMessages);
    }

    public void sendToChannel(final String channel, final String nick, final String message) {
        Set<ClientHandler> members = channelMembers.get(channel);
        if (members != null) {
            String formattedMessage = ":" + nick + "!" + nick + "@localhost PRIVMSG " + channel + " :" + message;
            for (ClientHandler client : members) {
                client.send(formattedMessage);
            }
        }
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                Thread.ofVirtual().start(handler);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    // Unexpected error during accept
                }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nick;
        private final Set<String> joinedChannels = new CopyOnWriteArraySet<>();

        ClientHandler(final Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                // Client disconnected
            } finally {
                close();
            }
        }

        private void handleLine(final String line) {
            IrcMessage msg = IrcParser.parse(line);
            switch (msg.command()) {
                case "NICK":
                    handleNick(msg);
                    break;
                case "USER":
                    handleUser(msg);
                    break;
                case "JOIN":
                    handleJoin(msg);
                    break;
                case "PART":
                    handlePart(msg);
                    break;
                case "PRIVMSG":
                    handlePrivmsg(msg);
                    break;
                case "NAMES":
                    handleNames(msg);
                    break;
                case "LIST":
                    handleList();
                    break;
                case "PING":
                    handlePing(msg);
                    break;
                case "QUIT":
                    close();
                    break;
            }
        }

        private void handleNick(final IrcMessage msg) {
            if (!msg.params().isEmpty()) {
                nick = msg.params().get(0);
            }
        }

        private void handleUser(final IrcMessage msg) {
            if (nick != null) {
                send(IrcParser.format(IrcCommand.RPL_WELCOME, nick, "Welcome to the IRC server " + nick));
            }
        }

        private void handleJoin(final IrcMessage msg) {
            if (!msg.params().isEmpty()) {
                String channel = msg.params().get(0);
                joinedChannels.add(channel);
                channelMembers.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(this);

                // Send NAMES reply
                Set<ClientHandler> members = channelMembers.get(channel);
                List<String> nicks = new ArrayList<>();
                for (ClientHandler member : members) {
                    if (member.nick != null) {
                        nicks.add(member.nick);
                    }
                }
                String nickList = String.join(" ", nicks);
                send(IrcParser.format(IrcCommand.RPL_NAMREPLY, nick, "=", channel, nickList));
                send(IrcParser.format(IrcCommand.RPL_ENDOFNAMES, nick, channel, "End of /NAMES list"));
            }
        }

        private void handlePart(final IrcMessage msg) {
            if (!msg.params().isEmpty()) {
                String channel = msg.params().get(0);
                joinedChannels.remove(channel);
                Set<ClientHandler> members = channelMembers.get(channel);
                if (members != null) {
                    members.remove(this);
                    if (members.isEmpty()) {
                        channelMembers.remove(channel);
                        channelTopics.remove(channel);
                    }
                }
            }
        }

        private void handlePrivmsg(final IrcMessage msg) {
            if (msg.params().size() >= 2) {
                String target = msg.params().get(0);
                String text = msg.params().get(1);
                receivedMessages.add(new ReceivedPrivmsg(nick, target, text));

                // Echo to other clients in the channel (not back to sender)
                if (target.startsWith("#")) {
                    Set<ClientHandler> members = channelMembers.get(target);
                    if (members != null) {
                        String formattedMessage = ":" + nick + "!" + nick + "@localhost PRIVMSG " + target + " :" + text;
                        for (ClientHandler member : members) {
                            if (member != this) {
                                member.send(formattedMessage);
                            }
                        }
                    }
                }
            }
        }

        private void handleNames(final IrcMessage msg) {
            if (!msg.params().isEmpty()) {
                String channel = msg.params().get(0);
                Set<ClientHandler> members = channelMembers.get(channel);
                if (members != null) {
                    List<String> nicks = new ArrayList<>();
                    for (ClientHandler member : members) {
                        if (member.nick != null) {
                            nicks.add(member.nick);
                        }
                    }
                    String nickList = String.join(" ", nicks);
                    send(IrcParser.format(IrcCommand.RPL_NAMREPLY, nick, "=", channel, nickList));
                }
                send(IrcParser.format(IrcCommand.RPL_ENDOFNAMES, nick, channel, "End of /NAMES list"));
            }
        }

        private void handleList() {
            for (Map.Entry<String, Set<ClientHandler>> entry : channelMembers.entrySet()) {
                String channel = entry.getKey();
                int memberCount = entry.getValue().size();
                String topic = channelTopics.getOrDefault(channel, "");
                send(IrcParser.format(IrcCommand.RPL_LIST, nick, channel, String.valueOf(memberCount), topic));
            }
            send(IrcParser.format(IrcCommand.RPL_LISTEND, nick, "End of /LIST"));
        }

        private void handlePing(final IrcMessage msg) {
            if (!msg.params().isEmpty()) {
                // PONG must echo back with colon prefix to match PING format
                send("PONG :" + msg.params().get(0));
            }
        }

        void send(final String message) {
            if (out != null) {
                out.println(message);
            }
        }

        void close() {
            try {
                // Remove from all channels
                for (String channel : joinedChannels) {
                    Set<ClientHandler> members = channelMembers.get(channel);
                    if (members != null) {
                        members.remove(this);
                        if (members.isEmpty()) {
                            channelMembers.remove(channel);
                            channelTopics.remove(channel);
                        }
                    }
                }
                joinedChannels.clear();

                // Close socket
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                // Remove from clients list
                clients.remove(this);
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
