package io.casehub.connectors.chat.degraded;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.Messaging;

class ChannelFallbackThreadingTest {

    @Test
    void replyDelegatesToMessagingSendWithParentChannel() {
        ChatChannelRef channel = new ChatChannelRef("general");
        ChatMessageRef parent = new ChatMessageRef(channel, "msg-1");
        ChatContent content = new ChatContent("reply text");
        SendResult expected = SendResult.success(
                new ChatMessageRef(channel, "msg-2"), Instant.now());

        Messaging mockMessaging = (ch, c) -> {
            assertThat(ch).isEqualTo(channel);
            assertThat(c).isEqualTo(content);
            return expected;
        };

        ChannelFallbackThreading threading = new ChannelFallbackThreading(mockMessaging);
        SendResult result = threading.reply(parent, content);

        assertThat(result).isEqualTo(expected);
    }
}
