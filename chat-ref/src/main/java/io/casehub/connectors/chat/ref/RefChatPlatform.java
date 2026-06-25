package io.casehub.connectors.chat.ref;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

@ApplicationScoped
public class RefChatPlatform implements ChatPlatform {

    private static final Set<Class<?>> ALL_CAPABILITIES = Set.of(
            Messaging.class, Threading.class, Discovery.class,
            Reactions.class, Presence.class, Members.class);

    final InMemoryStore store = new InMemoryStore();

    @Override public String id() { return "ref"; }

    @Override
    public Messaging messaging() {
        return (channel, content) -> store.store(channel, content, null);
    }

    @Override
    public Threading threading() {
        return (parent, content) -> store.store(parent.channel(), content, parent);
    }

    @Override
    public Discovery discovery() {
        return () -> List.copyOf(store.channels.values());
    }

    @Override
    public Reactions reactions() {
        return new Reactions() {
            @Override public void add(final ChatMessageRef message, final String emoji) {
                store.reactionsByMessage
                        .computeIfAbsent(message.messageId(), k -> new CopyOnWriteArrayList<>())
                        .add(emoji);
            }
            @Override public void remove(final ChatMessageRef message, final String emoji) {
                List<String> reactions = store.reactionsByMessage.get(message.messageId());
                if (reactions != null) reactions.remove(emoji);
            }
        };
    }

    @Override
    public Presence presence() {
        return member -> store.presenceByMember.getOrDefault(member.id(), PresenceStatus.UNKNOWN);
    }

    @Override
    public Members members() {
        return channel -> {
            List<MemberRef> refs = store.membersByChannel.get(channel.id());
            if (refs == null) return List.of();
            return refs.stream()
                    .map(ref -> new Member(ref, ref.id()))
                    .toList();
        };
    }

    @Override
    public boolean supports(final Class<?> capability) {
        return ALL_CAPABILITIES.contains(capability);
    }

    public void addChannel(final Channel channel) { store.addChannel(channel); }
    public void addMember(final String channelId, final MemberRef member) { store.addMember(channelId, member); }
    public void setPresence(final String memberId, final PresenceStatus status) { store.presenceByMember.put(memberId, status); }
    public List<String> getReactions(final String messageId) { return store.reactionsByMessage.getOrDefault(messageId, List.of()); }
}
