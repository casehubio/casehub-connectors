package io.casehub.connectors.discord.model;

import java.time.Instant;

public record DiscordMessage(String id, String channelId, DiscordUser author,
                             String content, Instant timestamp,
                             String referencedMessageId, int type) {}
