package io.casehub.connectors.chat.ref;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

@ApplicationScoped
public class RefChatPlatform implements ChatPlatform {

    private static final Set<Class<?>> ALL_CAPABILITIES = Set.of(
            Messaging.class, Threading.class, Discovery.class,
            Reactions.class, Presence.class, Members.class,
            ChannelManagement.class, MemberManagement.class, MessageHistory.class);

    @Inject
    ChatBackend backend;

    RefChatPlatform() {}

    public RefChatPlatform(final ChatBackend backend) {
        this.backend = backend;
    }

    @Override public String id() { return "ref"; }

    @Override
    public Messaging messaging() {
        return (channel, content) -> {
            var msg = backend.storeMessage(id(), channel, content, new MemberRef(id()), null);
            return SendResult.success(msg.messageRef(), msg.receivedAt());
        };
    }

    @Override
    public Threading threading() {
        return (parent, content) -> {
            var msg = backend.storeMessage(id(), parent.channel(), content, new MemberRef(id()), parent);
            return SendResult.success(msg.messageRef(), msg.receivedAt());
        };
    }

    @Override
    public Discovery discovery() {
        return backend::listChannels;
    }

    @Override
    public Reactions reactions() {
        return new Reactions() {
            @Override public void add(final ChatMessageRef message, final String emoji) {
                backend.addReaction(message, emoji);
            }
            @Override public void remove(final ChatMessageRef message, final String emoji) {
                backend.removeReaction(message, emoji);
            }
            @Override public List<String> list(final ChatMessageRef message) {
                return backend.reactions(message);
            }
        };
    }

    @Override
    public Presence presence() {
        return new Presence() {
            @Override public PresenceStatus of(final MemberRef member) {
                return backend.presence(member);
            }
            @Override public void set(final MemberRef member, final PresenceStatus status) {
                backend.setPresence(member, status);
            }
        };
    }

    @Override
    public Members members() {
        return backend::members;
    }

    @Override
    public ChannelManagement channelManagement() {
        return new ChannelManagement() {
            @Override public Channel create(final String name, final String topic,
                                            final String description, final boolean isPrivate) {
                return backend.createChannel(name, topic, description, isPrivate);
            }
            @Override public void delete(final String channelId) {
                backend.deleteChannel(channelId);
            }
            @Override public Optional<Channel> find(final String channelId) {
                return backend.findChannel(channelId);
            }
        };
    }

    @Override
    public MemberManagement memberManagement() {
        return new MemberManagement() {
            @Override public void add(final ChatChannelRef channel, final Member member) {
                backend.addMember(channel, member);
            }
            @Override public void remove(final ChatChannelRef channel, final MemberRef member) {
                backend.removeMember(channel, member);
            }
        };
    }

    @Override
    public MessageHistory messageHistory() {
        return backend::messages;
    }

    @Override
    public boolean supports(final Class<?> capability) {
        return ALL_CAPABILITIES.contains(capability);
    }
}
