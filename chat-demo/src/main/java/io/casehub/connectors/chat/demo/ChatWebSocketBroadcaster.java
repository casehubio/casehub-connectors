package io.casehub.connectors.chat.demo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.websockets.next.WebSocketConnection;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.ChatPlatform;

@ApplicationScoped
public class ChatWebSocketBroadcaster {

    private final Set<WebSocketConnection> connections = new CopyOnWriteArraySet<>();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChatPlatform chatPlatform;

    void addConnection(final WebSocketConnection connection) {
        connections.add(connection);
    }

    void removeConnection(final WebSocketConnection connection) {
        connections.remove(connection);
    }

    String buildSnapshot() {
        final var channels = chatPlatform.discovery().listChannels();
        final var channelRows = channels.stream()
                .map(ch -> List.<Object>of(ch.ref().id(), ch.name(), ch.topic(), ch.description(), ch.isPrivate()))
                .toList();

        final var messages = new java.util.ArrayList<List<Object>>();
        for (final Channel ch : channels) {
            for (final ReceivedMessage msg : chatPlatform.messageHistory().messages(ch.ref(), java.time.Instant.EPOCH)) {
                messages.add(messageToRow(msg));
            }
        }

        final var members = new java.util.ArrayList<List<Object>>();
        for (final Channel ch : channels) {
            for (final Member m : chatPlatform.members().list(ch.ref())) {
                members.add(List.of(ch.ref().id(), m.ref().id(), m.displayName()));
            }
        }

        return toJson(List.of(
                Map.of("dataset", "channels", "type", "snapshot", "rows", channelRows),
                Map.of("dataset", "messages", "type", "snapshot", "rows", messages),
                Map.of("dataset", "members", "type", "snapshot", "rows", members)));
    }

    void broadcastMessageAppend(final ReceivedMessage msg) {
        broadcast(Map.of(
                "dataset", "messages",
                "type", "append",
                "rows", List.of(messageToRow(msg))));
    }

    void broadcastChannelAppend(final Channel channel) {
        broadcast(Map.of(
                "dataset", "channels",
                "type", "append",
                "rows", List.of(List.<Object>of(
                        channel.ref().id(), channel.name(), channel.topic(),
                        channel.description(), channel.isPrivate()))));
    }

    void broadcastPresenceReplace(final MemberRef member, final PresenceStatus status) {
        broadcast(Map.of(
                "dataset", "presence",
                "type", "replace",
                "key", member.id(),
                "row", List.of(member.id(), status.name())));
    }

    void broadcastMemberAppend(final String channelId, final Member member) {
        broadcast(Map.of(
                "dataset", "members",
                "type", "append",
                "rows", List.of(List.of(channelId, member.ref().id(), member.displayName()))));
    }

    void broadcastMemberRemove(final String channelId, final MemberRef member) {
        broadcast(Map.of(
                "dataset", "members",
                "type", "remove",
                "key", channelId + ":" + member.id()));
    }

    void broadcastReactionAppend(final String messageId, final String emoji) {
        broadcast(Map.of(
                "dataset", "reactions",
                "type", "append",
                "rows", List.of(List.of(messageId, emoji))));
    }

    private void broadcast(final Object event) {
        final String json = toJson(event);
        connections.forEach(c -> c.sendText(json).subscribe().with(
                ignored -> {},
                err -> Log.warnf("WebSocket send failed: %s", err.getMessage())));
    }

    private List<Object> messageToRow(final ReceivedMessage msg) {
        return List.of(
                msg.channel().id(),
                msg.messageRef().messageId(),
                msg.parentRef() != null ? msg.parentRef().messageId() : "",
                msg.sender().id(),
                msg.content().text(),
                msg.receivedAt().toString());
    }

    private String toJson(final Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException("JSON serialisation failed", e);
        }
    }
}
