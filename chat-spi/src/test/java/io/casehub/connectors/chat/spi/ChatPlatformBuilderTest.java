package io.casehub.connectors.chat.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.SendResult;

class ChatPlatformBuilderTest {

    private static final Messaging STUB_MESSAGING = (ch, c) ->
            SendResult.success(new ChatMessageRef(ch, "stub"), Instant.now());

    @Test
    void buildFailsWithoutMessaging() {
        assertThatThrownBy(() -> ChatPlatform.builder("test").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messaging");
    }

    @Test
    void buildWithOnlyMessagingAutoDegrades() {
        ChatPlatform platform = ChatPlatform.builder("irc")
                .messaging(STUB_MESSAGING)
                .build();

        assertThat(platform.id()).isEqualTo("irc");
        assertThat(platform.messaging()).isEqualTo(STUB_MESSAGING);
        assertThat(platform.threading()).isInstanceOf(ChannelFallbackThreading.class);
        assertThat(platform.discovery()).isInstanceOf(EmptyDiscovery.class);
        assertThat(platform.reactions()).isInstanceOf(NoOpReactions.class);
        assertThat(platform.presence()).isInstanceOf(UnknownPresence.class);
        assertThat(platform.members()).isInstanceOf(EmptyMembers.class);
    }

    @Test
    void supportsReturnsTrueForExplicitlyProvided() {
        ChatPlatform platform = ChatPlatform.builder("test")
                .messaging(STUB_MESSAGING)
                .build();

        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isFalse();
        assertThat(platform.supports(Discovery.class)).isFalse();
        assertThat(platform.supports(Reactions.class)).isFalse();
        assertThat(platform.supports(Presence.class)).isFalse();
        assertThat(platform.supports(Members.class)).isFalse();
    }

    @Test
    void supportsReturnsTrueForAllWhenFullyProvided() {
        Threading threading = (parent, content) -> SendResult.success(
                new ChatMessageRef(parent.channel(), "reply"), Instant.now());

        ChatPlatform platform = ChatPlatform.builder("full")
                .messaging(STUB_MESSAGING)
                .threading(threading)
                .discovery(java.util.List::of)
                .reactions(new NoOpReactions())
                .presence(m -> io.casehub.connectors.chat.model.PresenceStatus.ONLINE)
                .members(ch -> java.util.List.of())
                .build();

        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Reactions.class)).isTrue();
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
    }

    @Test
    void channelFallbackThreadingDelegatesToMessaging() {
        ChatChannelRef channel = new ChatChannelRef("ch1");
        ChatMessageRef parent = new ChatMessageRef(channel, "msg1");
        ChatContent content = new ChatContent("hello");

        ChatPlatform platform = ChatPlatform.builder("degraded")
                .messaging(STUB_MESSAGING)
                .build();

        SendResult result = platform.threading().reply(parent, content);
        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef().channel()).isEqualTo(channel);
    }
}
