package io.casehub.connectors.graphql;

import io.casehub.connectors.SentMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SentMessageCaptureTest {

    @Test
    void capturesAndQueriesMessages() {
        var capture = new SentMessageCapture();
        var now = Instant.now();

        capture.onSent(new SentMessage("slack", "dest1", null, "hello", now, true));
        capture.onSent(new SentMessage("email", "dest2", "Subject", "body", now, true));

        var all = capture.query(null, 50);
        assertThat(all).hasSize(2);
        assertThat(all.getFirst().connectorId()).isEqualTo("email");
    }

    @Test
    void filtersById() {
        var capture = new SentMessageCapture();
        var now = Instant.now();

        capture.onSent(new SentMessage("slack", "d1", null, "a", now, true));
        capture.onSent(new SentMessage("email", "d2", null, "b", now, true));

        assertThat(capture.query("slack", 50)).hasSize(1);
        assertThat(capture.query("slack", 50).getFirst().body()).isEqualTo("a");
    }

    @Test
    void respectsLimit() {
        var capture = new SentMessageCapture();
        var now = Instant.now();

        for (int i = 0; i < 10; i++) {
            capture.onSent(new SentMessage("s", "d", null, "m" + i, now, true));
        }

        assertThat(capture.query(null, 3)).hasSize(3);
    }

    @Test
    void bufferBoundsAtMaxSize() {
        var capture = new SentMessageCapture();
        var now = Instant.now();

        for (int i = 0; i < 600; i++) {
            capture.onSent(new SentMessage("s", "d", null, "m" + i, now, true));
        }

        assertThat(capture.query(null, 1000).size()).isLessThanOrEqualTo(SentMessageCapture.MAX_SIZE);
    }
}
