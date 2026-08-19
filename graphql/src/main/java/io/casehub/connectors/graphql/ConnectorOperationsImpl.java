package io.casehub.connectors.graphql;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;
import io.casehub.connectors.graphql.dto.ChatPlatformInfo;
import io.casehub.connectors.graphql.dto.ConnectorStatusResult;
import io.casehub.connectors.graphql.dto.InboundConnectorInfo;
import io.casehub.connectors.graphql.dto.InjectChatResult;
import io.casehub.connectors.graphql.dto.OutboundConnectorInfo;
import io.casehub.connectors.graphql.dto.SendNotificationResult;
import io.casehub.connectors.graphql.dto.SentMessageEntry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConnectorOperationsImpl implements ConnectorOperations {

    private final InboundConnectorService inboundService;
    private final ConnectorService connectorService;
    private final ChatPlatformService chatPlatformService;
    private final List<Connector> connectors;
    private final List<WebhookInboundConnector> webhookConnectors;
    private final Instance<SentMessageCapture> sentMessageCapture;
    private final CurrentPrincipal currentPrincipal;

    @Inject
    public ConnectorOperationsImpl(
            final InboundConnectorService inboundService,
            final ConnectorService connectorService,
            final ChatPlatformService chatPlatformService,
            @All final List<Connector> connectors,
            @All final List<WebhookInboundConnector> webhookConnectors,
            final Instance<SentMessageCapture> sentMessageCapture,
            final CurrentPrincipal currentPrincipal) {
        this.inboundService = inboundService;
        this.connectorService = connectorService;
        this.chatPlatformService = chatPlatformService;
        this.connectors = connectors;
        this.webhookConnectors = webhookConnectors;
        this.sentMessageCapture = sentMessageCapture;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public InjectChatResult injectChat(final String platform, final String sender,
                                       final String channel, final String text) {
        if (!chatPlatformService.supports(platform)) {
            throw new IllegalArgumentException(
                    "Unknown chat platform '" + platform
                    + "'. Available: " + chatPlatformService.ids());
        }

        String tenancyId = currentPrincipal != null ? currentPrincipal.tenancyId() : null;

        var message = new InboundMessage(
                InboundConnectorIds.CHAT_INJECT,
                platform,
                sender,
                channel,
                text,
                List.of(),
                Instant.now(),
                Map.of("source", "mcp-inject"),
                tenancyId);

        inboundService.receive(message);
        return new InjectChatResult(true, platform, channel);
    }

    @Override
    public SendNotificationResult sendNotification(
            final String connectorId, final String destination,
            final String body, final String title,
            final Map<String, String> attributes) {
        var message = new ConnectorMessage(
                destination, title, body,
                attributes != null ? attributes : Map.of());
        boolean ok = connectorService.send(connectorId, message);
        return new SendNotificationResult(ok, connectorId, destination);
    }

    @Override
    public ConnectorStatusResult connectorStatus() {
        var outbound = connectors.stream()
                .map(c -> new OutboundConnectorInfo(c.id(), c.channelType()))
                .toList();

        var chatPlatforms = new ArrayList<ChatPlatformInfo>();
        for (String id : chatPlatformService.ids()) {
            ChatPlatform p = chatPlatformService.platform(id);
            var caps = new ArrayList<String>();
            if (p.supports(Messaging.class)) caps.add("Messaging");
            if (p.supports(Threading.class)) caps.add("Threading");
            if (p.supports(Discovery.class)) caps.add("Discovery");
            if (p.supports(Reactions.class)) caps.add("Reactions");
            if (p.supports(Presence.class)) caps.add("Presence");
            if (p.supports(Members.class)) caps.add("Members");
            if (p.supports(ChannelManagement.class)) caps.add("ChannelManagement");
            if (p.supports(MemberManagement.class)) caps.add("MemberManagement");
            if (p.supports(MessageHistory.class)) caps.add("MessageHistory");
            chatPlatforms.add(new ChatPlatformInfo(id, List.copyOf(caps)));
        }

        var inbound = new ArrayList<InboundConnectorInfo>();
        for (String id : inboundService.pullIds()) {
            inbound.add(new InboundConnectorInfo(id, "pull"));
        }
        for (WebhookInboundConnector wc : webhookConnectors) {
            inbound.add(new InboundConnectorInfo(wc.id(), "webhook"));
        }

        return new ConnectorStatusResult(outbound, chatPlatforms, List.copyOf(inbound));
    }

    @Override
    public List<SentMessageEntry> sentMessages(final String connectorId, final Integer limit) {
        if (sentMessageCapture == null || !sentMessageCapture.isResolvable()) {
            return List.of();
        }
        int effectiveLimit = limit != null ? limit : 50;
        return sentMessageCapture.get().query(connectorId, effectiveLimit);
    }
}
