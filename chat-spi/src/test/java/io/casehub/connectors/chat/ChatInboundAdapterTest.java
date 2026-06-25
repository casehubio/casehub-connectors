package io.casehub.connectors.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

class ChatInboundAdapterTest {

    @Test
    void translatesMatchingConnectorType() {
        List<ReceivedMessage> received = new ArrayList<>();
        Event<ReceivedMessage> mockEvent = createRecordingEvent(received);

        InboundTranslator translator = new InboundTranslator() {
            @Override public String connectorType() { return "test-chat"; }
            @Override public ReceivedMessage translate(InboundMessage msg) {
                return new ReceivedMessage("test-chat",
                        new ChatChannelRef(msg.externalChannelRef()),
                        new ChatMessageRef(new ChatChannelRef(msg.externalChannelRef()), "m1"),
                        null, new MemberRef(msg.externalSenderId()),
                        new ChatContent(msg.content()), msg.receivedAt());
            }
        };

        ChatInboundAdapter adapter = new ChatInboundAdapter(List.of(translator), mockEvent);

        InboundMessage msg = new InboundMessage("test-inbound", "test-chat",
                "user1", "channel1", "hello", List.of(), Instant.now(), Map.of(), null);

        adapter.onMessage(msg);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).platformId()).isEqualTo("test-chat");
        assertThat(received.get(0).content().text()).isEqualTo("hello");
    }

    @Test
    void ignoresNonMatchingConnectorType() {
        List<ReceivedMessage> received = new ArrayList<>();
        Event<ReceivedMessage> mockEvent = createRecordingEvent(received);

        InboundTranslator translator = new InboundTranslator() {
            @Override public String connectorType() { return "test-chat"; }
            @Override public ReceivedMessage translate(InboundMessage msg) {
                throw new AssertionError("Should not be called");
            }
        };

        ChatInboundAdapter adapter = new ChatInboundAdapter(List.of(translator), mockEvent);

        InboundMessage msg = new InboundMessage("email-inbound", "email",
                "sender", "inbox", "hi", List.of(), Instant.now(), Map.of(), null);

        adapter.onMessage(msg);

        assertThat(received).isEmpty();
    }

    @Test
    void translationFailureIsSwallowed() {
        List<ReceivedMessage> received = new ArrayList<>();
        Event<ReceivedMessage> mockEvent = createRecordingEvent(received);

        InboundTranslator translator = new InboundTranslator() {
            @Override public String connectorType() { return "broken"; }
            @Override public ReceivedMessage translate(InboundMessage msg) {
                throw new RuntimeException("parse error");
            }
        };

        ChatInboundAdapter adapter = new ChatInboundAdapter(List.of(translator), mockEvent);

        InboundMessage msg = new InboundMessage("broken-inbound", "broken",
                "user", "ch", "text", List.of(), Instant.now(), Map.of(), null);

        adapter.onMessage(msg);

        assertThat(received).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Event<ReceivedMessage> createRecordingEvent(final List<ReceivedMessage> received) {
        return new Event<>() {
            @Override public void fire(final ReceivedMessage event) { received.add(event); }
            @Override public <U extends ReceivedMessage> CompletionStage<U> fireAsync(final U event) {
                received.add(event);
                return (CompletableFuture<U>) CompletableFuture.completedFuture(event);
            }
            @Override public <U extends ReceivedMessage> CompletionStage<U> fireAsync(final U event,
                    final NotificationOptions options) {
                return fireAsync(event);
            }
            @Override public Event<ReceivedMessage> select(final java.lang.annotation.Annotation... qualifiers) { return this; }
            @Override public <U extends ReceivedMessage> Event<U> select(final Class<U> subtype, final java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
            @Override public <U extends ReceivedMessage> Event<U> select(final TypeLiteral<U> subtype, final java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        };
    }
}
