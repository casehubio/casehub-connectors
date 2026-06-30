package io.casehub.connectors.discord.model;

public record DiscordAttachment(String id, String filename,
        String contentType, long size, String url) {}
