package io.casehub.connectors.chat.spi;

import java.util.Set;

record DefaultChatPlatform(
        String id,
        Messaging messaging,
        Threading threading,
        Discovery discovery,
        Reactions reactions,
        Presence presence,
        Members members,
        ChannelManagement channelManagement,
        MemberManagement memberManagement,
        MessageHistory messageHistory,
        Set<Class<?>> nativeCapabilities) implements ChatPlatform {

    @Override
    public boolean supports(final Class<?> capability) {
        return nativeCapabilities.contains(capability);
    }
}
