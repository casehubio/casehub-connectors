package io.casehub.connectors.email.inbound;

/**
 * Configuration for one IMAP account to monitor via IDLE.
 *
 * <p>{@code id} appears in {@code InboundMessage.metadata["account-id"]}
 * (not in {@code connectorId} — that is always {@code "email-inbound"}).
 *
 * <p>{@code reconnectDelaySeconds} caps the exponential backoff applied between
 * connection attempts when the IMAP IDLE connection drops.
 *
 * <p>{@code tenancyId} identifies the tenant this account belongs to; null in
 * single-tenant deployments.
 */
public record EmailInboundAccount(
        String id,
        String host,
        int port,
        boolean tls,
        String username,
        String password,
        String folder,
        int reconnectDelaySeconds,
        String tenancyId) {
}
