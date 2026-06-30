package io.casehub.connectors.discord.model;

import java.time.Instant;
import java.util.List;

public record DiscordMessage(String id, String channelId, DiscordUser author,
                             String content, Instant timestamp,
                             String referencedMessageId, int type,
                             List<DiscordAttachment> attachments) {

    public DiscordMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
