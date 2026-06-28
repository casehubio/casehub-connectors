package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;

public interface Presence {
    PresenceStatus of(final MemberRef member);
    void set(final MemberRef member, final PresenceStatus status);
}
