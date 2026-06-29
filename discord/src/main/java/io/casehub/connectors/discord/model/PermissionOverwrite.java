package io.casehub.connectors.discord.model;

public record PermissionOverwrite(String id, int type, long allow, long deny) {}
