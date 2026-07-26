package io.casehub.connectors.notification;

import io.casehub.connectors.Connector;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DestinationResolver;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Startup
@ApplicationScoped
public class NotificationBridgeStartup {

    private static final Logger LOG = Logger.getLogger(NotificationBridgeStartup.class);

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "email", "Email",
            "sms", "SMS",
            "slack", "Slack",
            "teams", "Teams",
            "whatsapp", "WhatsApp");

    private static final Map<String, NotificationSeverity> RETRY_POLICIES = Map.of(
            "email", NotificationSeverity.WARNING,
            "sms", NotificationSeverity.WARNING);

    private final List<Connector>                        connectors;
    private final List<DestinationResolver>              resolvers;
    private final DeliveryChannelRegistry                channelRegistry;
    private final org.eclipse.microprofile.config.Config config;

    @Inject
    public NotificationBridgeStartup(@All List<Connector> connectors,
                                     @All List<DestinationResolver> resolvers,
                                     DeliveryChannelRegistry channelRegistry,
                                     org.eclipse.microprofile.config.Config config) {
        this.connectors      = connectors;
        this.resolvers       = resolvers;
        this.channelRegistry = channelRegistry;
        this.config          = config;
    }

    @PostConstruct
    void registerBridgedChannels() {
        Map<String, DestinationResolver> resolverIndex = new HashMap<>();
        for (DestinationResolver r : resolvers) {
            resolverIndex.put(r.channelId(), r);
        }

        Map<String, String> seenTypes = new HashMap<>();
        for (Connector connector : connectors) {
            String channelType = connector.channelType();
            if (channelType == null) {
                continue;
            }

            String previous = seenTypes.put(channelType, connector.id());
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate channel type '" + channelType
                        + "' — connectors '" + previous + "' and '"
                        + connector.id() + "' both declare it");
            }

            DestinationResolver resolver = resolverIndex.get(channelType);
            if (resolver == null) {
                Map<String, String> configDests = scanConfigDestinations(channelType);
                if (!configDests.isEmpty()) {
                    resolver = new ConfigDestinationResolver(channelType, configDests);
                }
            }

            var deliverer = new ConnectorNotificationDeliverer(
                    connector, channelType, resolver);

            var descriptor = new DeliveryChannelDescriptor(
                    channelType,
                    DISPLAY_NAMES.getOrDefault(channelType, channelType),
                    true,
                    false,
                    NotificationSeverity.WARNING,
                    null,
                    RETRY_POLICIES.get(channelType));

            channelRegistry.register(descriptor, deliverer);
            LOG.infof("Bridged connector '%s' as notification channel '%s'%s",
                      connector.id(), channelType,
                      resolver != null ? " (resolver: " + resolver.getClass().getSimpleName() + ")"
                                       : " (no resolver)");
        }
    }

    private Map<String, String> scanConfigDestinations(String channelType) {
        String              prefix       = "casehub.notification.destinations." + channelType + ".";
        Map<String, String> destinations = new HashMap<>();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith(prefix)) {
                destinations.put(name.substring(prefix.length()),
                                 config.getValue(name, String.class));
            }
        }
        return destinations;
    }
}
