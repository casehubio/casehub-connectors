package io.casehub.connectors.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SendResultTest {

    @Test
    void successRequiresNonNullRef() {
        assertThatThrownBy(() -> SendResult.success(null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageRef");
    }

    @Test
    void successCarriesRefAndTimestamp() {
        ChatChannelRef ch = new ChatChannelRef("C1");
        ChatMessageRef ref = new ChatMessageRef(ch, "ts1");
        Instant now = Instant.now();
        SendResult result = SendResult.success(ref, now);
        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isEqualTo(ref);
        assertThat(result.timestamp()).isEqualTo(now);
        assertThat(result.error()).isNull();
    }

    @Test
    void failureCarriesErrorMessage() {
        SendResult result = SendResult.failure("timeout");
        assertThat(result.ok()).isFalse();
        assertThat(result.messageRef()).isNull();
        assertThat(result.timestamp()).isNull();
        assertThat(result.error()).isEqualTo("timeout");
    }
}
