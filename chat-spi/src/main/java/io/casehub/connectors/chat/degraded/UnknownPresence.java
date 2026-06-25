package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.spi.Presence;

public class UnknownPresence implements Presence {
    @Override
    public PresenceStatus of(final MemberRef member) {
        return PresenceStatus.UNKNOWN;
    }
}
