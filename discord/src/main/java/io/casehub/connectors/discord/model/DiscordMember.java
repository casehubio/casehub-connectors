package io.casehub.connectors.discord.model;

import java.time.Instant;
import java.util.List;

public record DiscordMember(DiscordUser user, String nick, List<String> roles,
                            Instant joinedAt) {
    public DiscordMember {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
