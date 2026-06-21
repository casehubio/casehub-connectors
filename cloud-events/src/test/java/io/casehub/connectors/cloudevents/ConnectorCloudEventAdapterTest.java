package io.casehub.connectors.cloudevents;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.cloudevents.CloudEvent;

class ConnectorCloudEventAdapterTest {

    private final List<CloudEvent> fired = new ArrayList<>();
    private ConnectorCloudEventAdapter adapter;

    @BeforeEach
    void setUp() {
        jakarta.enterprise.event.Event<CloudEvent> mockEvent = new jakarta.enterprise.event.Event<>() {
            @Override public void fire(CloudEvent event) { fired.add(event); }
            @Override public CompletionStage<CloudEvent> fireAsync(CloudEvent event) {
                fired.add(event);
                return CompletableFuture.completedFuture(event);
            }
            @Override public CompletionStage<CloudEvent> fireAsync(CloudEvent event,
                    jakarta.enterprise.event.NotificationOptions options) {
                return fireAsync(event);
            }
            @Override public jakarta.enterprise.event.Event<CloudEvent> select(
                    java.lang.annotation.Annotation... qualifiers) { return this; }
            @Override public <U extends CloudEvent> jakarta.enterprise.event.Event<U> select(
                    Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
            @Override public <U extends CloudEvent> jakarta.enterprise.event.Event<U> select(
                    jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        };
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        adapter = new ConnectorCloudEventAdapter(mockEvent, mapper);
    }

    @Test
    void onMessage_producesCloudEventWithCorrectType() {
        adapter.onMessage(slackMessage("tenant-1"));

        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).getType()).isEqualTo("io.casehub.connectors.inbound.slack");
    }

    @Test
    void onMessage_sourceContainsConnectorId() {
        adapter.onMessage(slackMessage(null));

        assertThat(fired.get(0).getSource().toString()).isEqualTo("/casehub-connectors/slack-inbound");
    }

    @Test
    void onMessage_subjectContainsChannelRef() {
        adapter.onMessage(slackMessage(null));

        assertThat(fired.get(0).getSubject()).isEqualTo("channel/C456");
    }

    @Test
    void onMessage_tenancyIdSetWhenPresent() {
        adapter.onMessage(slackMessage("tenant-42"));

        assertThat(fired.get(0).getExtension("tenancyid")).isEqualTo("tenant-42");
    }

    @Test
    void onMessage_tenancyIdOmittedWhenNull() {
        adapter.onMessage(slackMessage(null));

        assertThat(fired.get(0).getExtension("tenancyid")).isNull();
    }

    @Test
    void onMessage_dataIsJsonSerialised() {
        adapter.onMessage(slackMessage(null));

        CloudEvent ce = fired.get(0);
        assertThat(ce.getData()).isNotNull();
        assertThat(ce.getData().toBytes().length).isGreaterThan(0);
        assertThat(ce.getDataContentType()).isEqualTo("application/json");
    }

    @Test
    void onMessage_timeFromReceivedAt() {
        Instant now = Instant.now();
        InboundMessage msg = new InboundMessage(
                InboundConnectorIds.SLACK_INBOUND, InboundConnectorTypes.SLACK,
                "U123", "C456", "hello", List.of(), now, Map.of(), null);
        adapter.onMessage(msg);

        CloudEvent ce = fired.get(0);
        assertThat(ce.getTime()).isNotNull();
        assertThat(ce.getTime().toInstant()).isEqualTo(now);
    }

    @Test
    void onMessage_idIsUUID() {
        adapter.onMessage(slackMessage(null));

        assertThat(fired.get(0).getId()).matches("[0-9a-f\\-]{36}");
    }

    private static InboundMessage slackMessage(String tenancyId) {
        return new InboundMessage(
                InboundConnectorIds.SLACK_INBOUND, InboundConnectorTypes.SLACK,
                "U123", "C456", "hello", List.of(), Instant.now(), Map.of(), tenancyId);
    }
}
