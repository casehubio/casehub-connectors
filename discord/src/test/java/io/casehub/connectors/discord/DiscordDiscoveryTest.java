package io.casehub.connectors.discord;

import io.casehub.connectors.DiscoveredTarget;
import io.casehub.connectors.discord.model.DiscordChannel;
import io.casehub.connectors.discord.model.DiscordGuild;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordDiscoveryTest {

    private static final String TOKEN = "test-token";

    @Test
    void id_returnsDiscord() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(List.of(), Map.of()), TOKEN);
        assertThat(discovery.id()).isEqualTo("discord");
    }

    @Test
    void discover_blankTokenReturnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(List.of(), Map.of()), "");
        final var result    = discovery.discover();
        assertThat(result).isEmpty();
    }

    @Test
    void discover_returnsTextChannels() {
        final var guilds = List.of(new DiscordGuild("g1", "Guild 1", null));
        final var channels = Map.of("g1", List.of(
                new DiscordChannel("ch1", "g1", "general", "General chat", 0, null, List.of()),
                new DiscordChannel("ch2", "g1", "announcements", "News", 5, null, List.of()),
                new DiscordChannel("th1", "g1", "thread-1", "A thread", 11, null, List.of())
                                                 ));
        final var discovery = new DiscordDiscovery(new StubDiscordClient(guilds, channels), TOKEN);
        final var result    = discovery.discover();

        assertThat(result).hasSize(3);
        assertThat(result).contains(
                new DiscoveredTarget("ch1", "#general"),
                new DiscoveredTarget("ch2", "#announcements"),
                new DiscoveredTarget("th1", "#thread-1")
                                   );
    }

    @Test
    void discover_filtersOutNonTextChannels() {
        final var guilds = List.of(new DiscordGuild("g1", "Guild 1", null));
        final var channels = Map.of("g1", List.of(
                new DiscordChannel("ch1", "g1", "general", "General chat", 0, null, List.of()),
                new DiscordChannel("voice", "g1", "voice-lounge", "Voice channel", 2, null, List.of()),
                new DiscordChannel("cat", "g1", "category-1", "A category", 4, null, List.of()),
                new DiscordChannel("forum", "g1", "my-forum", "Forum channel", 15, null, List.of())
                                                 ));
        final var discovery = new DiscordDiscovery(new StubDiscordClient(guilds, channels), TOKEN);
        final var result    = discovery.discover();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("ch1");
        assertThat(result.get(0).displayName()).isEqualTo("#general");
    }

    @Test
    void discover_nullFromListBotGuilds_returnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(null, Map.of()), TOKEN);
        final var result    = discovery.discover();
        assertThat(result).isEmpty();
    }

    @Test
    void discover_emptyGuildList_returnsEmpty() {
        final var discovery = new DiscordDiscovery(new StubDiscordClient(List.of(), Map.of()), TOKEN);
        final var result    = discovery.discover();
        assertThat(result).isEmpty();
    }

    @Test
    void discover_multiGuild_aggregatesChannels() {
        final var guilds = List.of(
                new DiscordGuild("g1", "Guild 1", null),
                new DiscordGuild("g2", "Guild 2", null));
        final var channels = Map.of(
                "g1", List.of(new DiscordChannel("ch1", "g1", "general", null, 0, null, List.of())),
                "g2", List.of(new DiscordChannel("ch2", "g2", "lobby", null, 0, null, List.of())));
        final var discovery = new DiscordDiscovery(new StubDiscordClient(guilds, channels), TOKEN);
        final var result    = discovery.discover();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DiscoveredTarget::id).containsExactlyInAnyOrder("ch1", "ch2");
    }

    private static class StubDiscordClient extends DiscordClient {
        private final List<DiscordGuild>                guilds;
        private final Map<String, List<DiscordChannel>> channelsByGuild;

        StubDiscordClient(final List<DiscordGuild> guilds,
                          final Map<String, List<DiscordChannel>> channelsByGuild) {
            this.guilds          = guilds;
            this.channelsByGuild = channelsByGuild;
        }

        @Override
        public List<DiscordGuild> listBotGuilds(final String token) {
            return guilds;
        }

        @Override
        public List<DiscordChannel> listGuildChannels(final String token, final String guildId) {
            return channelsByGuild.getOrDefault(guildId, List.of());
        }
    }
}
