package io.casehub.connectors.chat.ref;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.SendResult;

class InMemoryStore {

    record StoredMessage(ChatMessageRef ref, ChatChannelRef channel, ChatContent content,
                         ChatMessageRef parentRef, Instant timestamp) {}

    final Map<String, Channel> channels = new ConcurrentHashMap<>();
    final Map<String, List<StoredMessage>> messagesByChannel = new ConcurrentHashMap<>();
    final Map<String, List<String>> reactionsByMessage = new ConcurrentHashMap<>();
    final Map<String, PresenceStatus> presenceByMember = new ConcurrentHashMap<>();
    final Map<String, List<MemberRef>> membersByChannel = new ConcurrentHashMap<>();

    SendResult store(final ChatChannelRef channel, final ChatContent content,
                     final ChatMessageRef parentRef) {
        String messageId = UUID.randomUUID().toString();
        ChatMessageRef ref = new ChatMessageRef(channel, messageId);
        Instant now = Instant.now();
        StoredMessage stored = new StoredMessage(ref, channel, content, parentRef, now);
        messagesByChannel.computeIfAbsent(channel.id(), k -> new CopyOnWriteArrayList<>()).add(stored);
        return SendResult.success(ref, now);
    }

    void addChannel(final Channel channel) {
        channels.put(channel.ref().id(), channel);
    }

    void addMember(final String channelId, final MemberRef member) {
        membersByChannel.computeIfAbsent(channelId, k -> new CopyOnWriteArrayList<>()).add(member);
    }
}
