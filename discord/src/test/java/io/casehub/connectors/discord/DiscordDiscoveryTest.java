package io.casehub.connectors.discord;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.DiscoveredTarget;
import io.casehub.connectors.discord.model.DiscordChannel;

class DiscordDiscoveryTest {

    private static final String TOKEN = "test-token";

    @Test
    void id_returnsDiscord() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(), TOKEN);
        assertThat(discovery.id()).isEqualTo("discord");
    }

    @Test
    void discover_blankTokenReturnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(), "");
        final var result = discovery.discover();
        assertThat(result).isEmpty();
    }

    @Test
    void discover_returnsTextChannels() {
        final var channels = List.of(
                new DiscordChannel("ch1", "general", "General chat", 0, null, List.of()),
                new DiscordChannel("ch2", "announcements", "News", 5, null, List.of()),
                new DiscordChannel("th1", "thread-1", "A thread", 11, null, List.of())
        );
        final var client = new StubDiscordClient(channels);
        final var discovery = new DiscordDiscovery(client, TOKEN);
        final var result = discovery.discover();

        assertThat(result).hasSize(3);
        assertThat(result).contains(
                new DiscoveredTarget("ch1", "#general"),
                new DiscoveredTarget("ch2", "#announcements"),
                new DiscoveredTarget("th1", "#thread-1")
        );
    }

    @Test
    void discover_filtersOutNonTextChannels() {
        final var channels = List.of(
                new DiscordChannel("ch1", "general", "General chat", 0, null, List.of()),
                new DiscordChannel("voice", "voice-lounge", "Voice channel", 2, null, List.of()),
                new DiscordChannel("cat", "category-1", "A category", 4, null, List.of()),
                new DiscordChannel("forum", "my-forum", "Forum channel", 15, null, List.of())
        );
        final var client = new StubDiscordClient(channels);
        final var discovery = new DiscordDiscovery(client, TOKEN);
        final var result = discovery.discover();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("ch1");
        assertThat(result.get(0).displayName()).isEqualTo("#general");
    }

    @Test
    void discover_nullClientResponseReturnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(null), TOKEN);
        final var result = discovery.discover();
        assertThat(result).isEmpty();
    }

    @Test
    void discover_emptyClientResponseReturnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(List.of()), TOKEN);
        final var result = discovery.discover();
        assertThat(result).isEmpty();
    }

    // Stub for testing without external HTTP calls
    private static class StubDiscordClient extends DiscordClient {
        private final List<DiscordChannel> channels;

        StubDiscordClient() {
            this(null);
        }

        StubDiscordClient(final List<DiscordChannel> channels) {
            this.channels = channels;
        }

        @Override
        public List<DiscordChannel> listGuildChannels(final String token) {
            return channels;
        }
    }
}
