package io.casehub.connectors.chat.irc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.InboundConnector;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.InboundMessageSink;
import io.casehub.connectors.chat.irc.protocol.IrcMessage;

@ApplicationScoped
public class IrcInboundConnector implements InboundConnector {

    private static final Logger LOG = Logger.getLogger(
            IrcInboundConnector.class.getName());

    private final IrcClient client;
    private final Optional<List<String>> channels;
    private volatile boolean stopping = false;
    private volatile ExecutorService executor;

    @Inject
    public IrcInboundConnector(
            final IrcClient client,
            @ConfigProperty(name = "casehub.connectors.chat-irc.channels")
            final Optional<List<String>> channels) {
        this.client = client;
        this.channels = channels;
    }

    @Override
    public String id() {
        return InboundConnectorIds.IRC;
    }

    @Override
    public void start(final InboundMessageSink sink) {
        if (executor != null) return;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(() -> connectLoop(sink));
    }

    @Override
    public void stop() {
        stopping = true;
        client.disconnect();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void connectLoop(final InboundMessageSink sink) {
        int backoffSeconds = 1;
        int consecutiveFailures = 0;

        while (!stopping) {
            try {
                client.setMessageCallback(msg -> deliverToSink(msg, sink));
                if (!client.connect()) {
                    throw new java.io.IOException("IRC connect failed");
                }
                backoffSeconds = 1;
                consecutiveFailures = 0;
                LOG.info("irc-inbound: connected");
                joinConfiguredChannels();
                awaitDisconnect();
            } catch (final Exception e) {
                if (!stopping) {
                    consecutiveFailures++;
                    Level level = consecutiveFailures >= 5
                            ? Level.SEVERE : Level.WARNING;
                    LOG.log(level, "irc-inbound: connection failed (attempt "
                            + consecutiveFailures + "): " + e.getMessage());
                    sleepQuietly(backoffSeconds * 1000L);
                    backoffSeconds = Math.min(backoffSeconds * 2, 60);
                }
            }
        }
    }

    private void joinConfiguredChannels() {
        channels.ifPresent(list -> list.forEach(ch -> {
            if (!client.join(ch)) {
                LOG.warning("irc-inbound: failed to join " + ch);
            }
        }));
    }

    private void awaitDisconnect() {
        while (client.isConnected() && !stopping) {
            sleepQuietly(1000);
        }
    }

    private void deliverToSink(final IrcMessage msg,
                                final InboundMessageSink sink) {
        String nick = extractNick(msg.prefix());
        String channel = msg.params().get(0);
        String text = msg.params().get(1);
        try {
            sink.receive(new InboundMessage(
                    InboundConnectorIds.IRC,
                    InboundConnectorTypes.IRC,
                    nick,
                    channel,
                    text,
                    List.of(),
                    Instant.now(),
                    Map.of("nick-prefix",
                            msg.prefix() != null ? msg.prefix() : ""),
                    null));
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, "irc-inbound: sink threw", e);
        }
    }

    static String extractNick(final String prefix) {
        if (prefix == null) return "";
        int bang = prefix.indexOf('!');
        return bang >= 0 ? prefix.substring(0, bang) : prefix;
    }

    private static void sleepQuietly(final long millis) {
        try { Thread.sleep(millis); }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
