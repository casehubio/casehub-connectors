package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorServiceEventTest {

    @Test
    void sendFiresSentMessageEvent() {
        var captured = new ArrayList<SentMessage>();
        var connector = new StubConnector("test-conn", true);
        var service = new ConnectorService(
                List.of(connector),
                captured::add);

        service.send("test-conn",
                new ConnectorMessage("dest@example.com", "Hello", "World"));

        assertThat(captured).hasSize(1);
        var sent = captured.getFirst();
        assertThat(sent.connectorId()).isEqualTo("test-conn");
        assertThat(sent.destination()).isEqualTo("dest@example.com");
        assertThat(sent.title()).isEqualTo("Hello");
        assertThat(sent.body()).isEqualTo("World");
        assertThat(sent.success()).isTrue();
    }

    @Test
    void sendFiresEventOnFailure() {
        var captured = new ArrayList<SentMessage>();
        var connector = new StubConnector("fail-conn", false);
        var service = new ConnectorService(
                List.of(connector),
                captured::add);

        service.send("fail-conn",
                new ConnectorMessage("dest", "Body"));

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst().success()).isFalse();
    }

    private record StubConnector(String id, boolean result) implements Connector {
        @Override
        public boolean send(ConnectorMessage message) {
            return result;
        }
    }
}
