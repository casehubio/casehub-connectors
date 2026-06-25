package io.casehub.connectors.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Messaging;

class ChatPlatformServiceTest {

    private static final Messaging STUB = (ch, c) ->
            SendResult.success(new ChatMessageRef(ch, "s"), Instant.now());

    @Test
    void routesByPlatformId() {
        ChatPlatform p = ChatPlatform.builder("test").messaging(STUB).build();
        ChatPlatformService service = new ChatPlatformService(List.of(p));

        assertThat(service.platform("test")).isEqualTo(p);
        assertThat(service.supports("test")).isTrue();
        assertThat(service.ids()).containsExactly("test");
    }

    @Test
    void throwsOnUnknownId() {
        ChatPlatformService service = new ChatPlatformService(List.of());
        assertThatThrownBy(() -> service.platform("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void throwsOnDuplicateId() {
        ChatPlatform a = ChatPlatform.builder("dup").messaging(STUB).build();
        ChatPlatform b = ChatPlatform.builder("dup").messaging(STUB).build();
        assertThatThrownBy(() -> new ChatPlatformService(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }
}
