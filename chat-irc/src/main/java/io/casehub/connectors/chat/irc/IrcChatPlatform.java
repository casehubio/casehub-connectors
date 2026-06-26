package io.casehub.connectors.chat.irc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

@ApplicationScoped
public class IrcChatPlatform implements ChatPlatform {

    private static final Set<Class<?>> NATIVE_CAPABILITIES = Set.of(
            Messaging.class, Discovery.class, Members.class);

    private final IrcClient client;
    private final Messaging messaging;
    private final Threading threading;
    private final Discovery discovery;
    private final Members members;
    private final Reactions reactions = new NoOpReactions();
    private final Presence presence = new UnknownPresence();

    @Inject
    public IrcChatPlatform(final IrcClient client) {
        this.client = client;
        this.messaging = (channel, content) -> {
            if (!client.send(channel.id(), content.text())) {
                return SendResult.failure("not connected");
            }
            return SendResult.success(
                    new ChatMessageRef(channel, UUID.randomUUID().toString()),
                    Instant.now());
        };
        this.threading = new ChannelFallbackThreading(messaging);
        this.discovery = () -> client.listChannels().stream()
                .map(ci -> new Channel(
                        new ChatChannelRef(ci.name()),
                        ci.name(),
                        ci.topic(),
                        false))
                .toList();
        this.members = channel -> client.names(channel.id()).stream()
                .map(nick -> new Member(new MemberRef(nick), nick))
                .toList();
    }

    @Override public String id() { return "irc"; }
    @Override public Messaging messaging() { return messaging; }
    @Override public Threading threading() { return threading; }
    @Override public Discovery discovery() { return discovery; }
    @Override public Reactions reactions() { return reactions; }
    @Override public Presence presence() { return presence; }
    @Override public Members members() { return members; }

    @Override
    public boolean supports(final Class<?> capability) {
        return NATIVE_CAPABILITIES.contains(capability);
    }
}
