package io.casehub.connectors.notification;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDestinationResolverTest {

    @Test
    void resolve_knownUser_returnsDestination() {
        var resolver = new ConfigDestinationResolver("email",
                Map.of("user-1", "user1@example.com"));
        assertThat(resolver.resolve("user-1", "tenant-1"))
                .hasValue("user1@example.com");
    }

    @Test
    void resolve_unknownUser_returnsEmpty() {
        var resolver = new ConfigDestinationResolver("email",
                Map.of("user-1", "user1@example.com"));
        assertThat(resolver.resolve("unknown", "tenant-1")).isEmpty();
    }

    @Test
    void resolve_ignoresTenancyId() {
        var resolver = new ConfigDestinationResolver("sms",
                Map.of("user-1", "+447700900000"));
        assertThat(resolver.resolve("user-1", "tenant-A"))
                .hasValue("+447700900000");
        assertThat(resolver.resolve("user-1", "tenant-B"))
                .hasValue("+447700900000");
    }

    @Test
    void channelId_returnsConstructorValue() {
        var resolver = new ConfigDestinationResolver("whatsapp", Map.of());
        assertThat(resolver.channelId()).isEqualTo("whatsapp");
    }

    @Test
    void resolve_emptyMap_alwaysReturnsEmpty() {
        var resolver = new ConfigDestinationResolver("email", Map.of());
        assertThat(resolver.resolve("user-1", "tenant-1")).isEmpty();
    }

    @Test
    void hasEntries_withEntries_returnsTrue() {
        var resolver = new ConfigDestinationResolver("email",
                Map.of("user-1", "user1@example.com"));
        assertThat(resolver.hasEntries()).isTrue();
    }

    @Test
    void hasEntries_emptyMap_returnsFalse() {
        var resolver = new ConfigDestinationResolver("email", Map.of());
        assertThat(resolver.hasEntries()).isFalse();
    }
}
