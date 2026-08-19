package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorServiceTest {

    private static class RecordingConnector implements Connector {
        final String connectorId;
        ConnectorMessage received;

        RecordingConnector(String id) {
            this.connectorId = id;
        }

        @Override
        public String id() {
            return connectorId;
        }

        @Override
        public boolean send(final ConnectorMessage msg) {
            this.received = msg;
            return true;
        }
    }

    @Test
    void send_routesToCorrectConnector() {
        final RecordingConnector slack = new RecordingConnector("slack");
        final RecordingConnector teams = new RecordingConnector("teams");
        final ConnectorService service = new ConnectorService(List.of(slack, teams), msg -> {});
        final ConnectorMessage msg = new ConnectorMessage("https://hooks.slack.com/x", "Alert", "Body");

        service.send("slack", msg);

        assertThat(slack.received).isSameAs(msg);
        assertThat(teams.received).isNull();
    }

    @Test
    void send_unknownId_throwsIllegalArgumentException() {
        final ConnectorService service = new ConnectorService(List.of(new RecordingConnector("slack")), msg -> {});

        assertThatThrownBy(() -> service.send("email", new ConnectorMessage("x@x.com", "body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("slack");
    }

    @Test
    void supports_knownId_returnsTrue() {
        final ConnectorService service = new ConnectorService(List.of(new RecordingConnector("slack")), msg -> {});
        assertThat(service.supports("slack")).isTrue();
    }

    @Test
    void supports_unknownId_returnsFalse() {
        final ConnectorService service = new ConnectorService(List.of(new RecordingConnector("slack")), msg -> {});
        assertThat(service.supports("email")).isFalse();
    }

    @Test
    void ids_returnsAllConnectorIds() {
        final ConnectorService service = new ConnectorService(
                List.of(new RecordingConnector("slack"), new RecordingConnector("teams")), msg -> {});
        assertThat(service.ids()).containsExactlyInAnyOrder("slack", "teams");
    }

    @Test
    void constructor_duplicateIds_throwsIllegalStateException() {
        assertThatThrownBy(() -> new ConnectorService(
                List.of(new RecordingConnector("slack"), new RecordingConnector("slack")), msg -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slack");
    }
}
