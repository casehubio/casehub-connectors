package io.casehub.connectors.discord.model;

public record PostResult(boolean ok, String messageId, String channelId, String error) {

    public static PostResult success(final String messageId, final String channelId) {
        return new PostResult(true, messageId, channelId, null);
    }

    public static PostResult failure(final String error) {
        return new PostResult(false, null, null, error);
    }
}
