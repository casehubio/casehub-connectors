package io.casehub.connectors;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A message received from an external system via an {@link InboundConnector}.
 *
 * <p>{@code connectorType} is always non-null — the stable semantic transport
 * category (e.g. {@code "slack"}, {@code "email"}). Used as the CloudEvent
 * {@code type} suffix. See {@link InboundConnectorTypes}.
 *
 * <p>{@code attachments} is always non-null; it is {@code List.of()} for connectors
 * that produce no attachments. Email inbound populates it from the MIME structure.
 *
 * <p>{@code tenancyId} is nullable — null in single-tenant deployments.
 */
public record InboundMessage(
        String connectorId,
        String connectorType,
        String externalSenderId,
        String externalChannelRef,
        String content,
        List<Attachment> attachments,
        Instant receivedAt,
        Map<String, String> metadata,
        String tenancyId) {

    public InboundMessage {
        Objects.requireNonNull(connectorType, "connectorType");
        attachments = List.copyOf(attachments);
    }
}
