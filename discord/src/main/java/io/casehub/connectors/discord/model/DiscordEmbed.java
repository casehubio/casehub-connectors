package io.casehub.connectors.discord.model;

import java.util.List;

public record DiscordEmbed(
        String title, String description, String url, Integer color,
        List<Field> fields, String thumbnailUrl, String imageUrl,
        Footer footer, Author author) {

    public record Field(String name, String value, boolean inline) {}
    public record Footer(String text) {}
    public record Author(String name) {}

    public DiscordEmbed {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
