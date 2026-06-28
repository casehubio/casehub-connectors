package io.casehub.connectors.chat.ref;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;

@DefaultBean
@ApplicationScoped
public class InMemoryChatBackend implements ChatBackend {

    final Map<String, Channel> channels = new ConcurrentHashMap<>();
    final Map<String, List<ReceivedMessage>> messagesByChannel = new ConcurrentHashMap<>();
    final Map<String, List<String>> reactionsByMessage = new ConcurrentHashMap<>();
    final Map<String, PresenceStatus> presenceByMember = new ConcurrentHashMap<>();
    final Map<String, List<Member>> membersByChannel = new ConcurrentHashMap<>();

    @Override
    public Channel createChannel(final String name, final String topic,
                                 final String description, final boolean isPrivate) {
        final String id = UUID.randomUUID().toString();
        final Channel channel = new Channel(new ChatChannelRef(id), name, topic, description, isPrivate);
        channels.put(id, channel);
        return channel;
    }

    @Override
    public Optional<Channel> findChannel(final String channelId) {
        return Optional.ofNullable(channels.get(channelId));
    }

    @Override
    public List<Channel> listChannels() {
        return List.copyOf(channels.values());
    }

    @Override
    public ReceivedMessage storeMessage(final String platformId, final ChatChannelRef channel,
                                         final ChatContent content, final MemberRef sender,
                                         final ChatMessageRef parentRef) {
        final String messageId = UUID.randomUUID().toString();
        final ChatMessageRef ref = new ChatMessageRef(channel, messageId);
        final Instant now = Instant.now();
        final ReceivedMessage msg = new ReceivedMessage(
                platformId, channel, ref, parentRef, sender, content, now);
        messagesByChannel.computeIfAbsent(channel.id(), k -> new CopyOnWriteArrayList<>()).add(msg);
        return msg;
    }

    @Override
    public List<ReceivedMessage> messages(final ChatChannelRef channel, final Instant since) {
        final List<ReceivedMessage> all = messagesByChannel.getOrDefault(channel.id(), List.of());
        return all.stream()
                .filter(m -> !m.receivedAt().isBefore(since))
                .toList();
    }

    @Override
    public void addReaction(final ChatMessageRef message, final String emoji) {
        final List<String> reactions = reactionsByMessage
                .computeIfAbsent(message.messageId(), k -> new CopyOnWriteArrayList<>());
        if (!reactions.contains(emoji)) {
            reactions.add(emoji);
        }
    }

    @Override
    public void removeReaction(final ChatMessageRef message, final String emoji) {
        final List<String> reactions = reactionsByMessage.get(message.messageId());
        if (reactions != null) {
            reactions.remove(emoji);
        }
    }

    @Override
    public List<String> reactions(final ChatMessageRef message) {
        return List.copyOf(reactionsByMessage.getOrDefault(message.messageId(), List.of()));
    }

    @Override
    public void setPresence(final MemberRef member, final PresenceStatus status) {
        presenceByMember.put(member.id(), status);
    }

    @Override
    public PresenceStatus presence(final MemberRef member) {
        return presenceByMember.getOrDefault(member.id(), PresenceStatus.UNKNOWN);
    }

    @Override
    public void addMember(final ChatChannelRef channel, final Member member) {
        membersByChannel
                .computeIfAbsent(channel.id(), k -> new CopyOnWriteArrayList<>())
                .add(member);
    }

    @Override
    public void removeMember(final ChatChannelRef channel, final MemberRef member) {
        final List<Member> members = membersByChannel.get(channel.id());
        if (members != null) {
            members.removeIf(m -> m.ref().equals(member));
        }
    }

    @Override
    public List<Member> members(final ChatChannelRef channel) {
        return List.copyOf(membersByChannel.getOrDefault(channel.id(), List.of()));
    }
}
