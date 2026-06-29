package io.casehub.connectors.discord.model;

import java.util.List;

public record DiscordChannel(String id, String name, String topic, int type,
                             String parentId,
                             List<PermissionOverwrite> permissionOverwrites) {
    public DiscordChannel {
        permissionOverwrites = permissionOverwrites == null
                ? List.of() : List.copyOf(permissionOverwrites);
    }
}
