package io.casehub.connectors.chat.model;

import java.util.List;

public record RichCard(
        String title,
        String description,
        String url,
        Integer color,
        List<Field> fields,
        String thumbnailUrl,
        String imageUrl,
        String footer,
        String author) {

    public record Field(String name, String value, boolean inline) {}

    public RichCard {
        if (title == null && description == null) {
            throw new IllegalArgumentException("RichCard requires at least title or description");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title;
        private String description;
        private String url;
        private Integer color;
        private List<Field> fields;
        private String thumbnailUrl;
        private String imageUrl;
        private String footer;
        private String author;

        Builder() {}

        public Builder title(final String title) { this.title = title; return this; }
        public Builder description(final String description) { this.description = description; return this; }
        public Builder url(final String url) { this.url = url; return this; }
        public Builder color(final Integer color) { this.color = color; return this; }
        public Builder fields(final List<Field> fields) { this.fields = fields; return this; }
        public Builder thumbnailUrl(final String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; return this; }
        public Builder imageUrl(final String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder footer(final String footer) { this.footer = footer; return this; }
        public Builder author(final String author) { this.author = author; return this; }

        public RichCard build() {
            return new RichCard(title, description, url, color, fields,
                    thumbnailUrl, imageUrl, footer, author);
        }
    }
}
