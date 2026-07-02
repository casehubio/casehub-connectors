package io.casehub.connectors.chat.ref;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;

public interface ChatBackend {

    Channel createChannel(String name, String topic, String description, boolean isPrivate);
    void deleteChannel(String channelId);
    Optional<Channel> findChannel(String channelId);
    List<Channel> listChannels();

    ReceivedMessage storeMessage(String platformId, ChatChannelRef channel, ChatContent content,
                                 MemberRef sender, ChatMessageRef parentRef);
    List<ReceivedMessage> messages(ChatChannelRef channel, Instant since);

    void addReaction(ChatMessageRef message, String emoji);
    void removeReaction(ChatMessageRef message, String emoji);
    List<String> reactions(ChatMessageRef message);

    void setPresence(MemberRef member, PresenceStatus status);
    PresenceStatus presence(MemberRef member);

    void addMember(ChatChannelRef channel, Member member);
    void removeMember(ChatChannelRef channel, MemberRef member);
    List<Member> members(ChatChannelRef channel);
}
