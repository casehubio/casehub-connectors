package io.casehub.connectors.chat.spi;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

public interface InboundTranslator {
    String connectorType();
    ReceivedMessage translate(final InboundMessage msg);
}
