package io.casehub.connectors.discord.model;

public record DiscordUser(String id, String username, String globalName, boolean bot) {}
