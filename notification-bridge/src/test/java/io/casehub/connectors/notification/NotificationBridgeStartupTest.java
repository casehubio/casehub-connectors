package io.casehub.connectors.notification;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DestinationScope;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.DestinationResolver;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationSeverity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationBridgeStartupTest {

    static final org.eclipse.microprofile.config.Config EMPTY_CONFIG = new StubConfig(java.util.Map.of());

    static class StubConfig implements org.eclipse.microprofile.config.Config {
        private final java.util.Map<String, String> values;

        StubConfig(java.util.Map<String, String> values) {this.values = values;}

        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            return propertyType.cast(values.get(propertyName));
        }

        @Override
        public org.eclipse.microprofile.config.ConfigValue getConfigValue(String propertyName) {return null;}

        @Override
        public <T> java.util.Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            return java.util.Optional.ofNullable(values.get(propertyName)).map(propertyType::cast);
        }

        @Override
        public Iterable<String> getPropertyNames()                                                                     {return values.keySet();}

        @Override
        public Iterable<org.eclipse.microprofile.config.spi.ConfigSource> getConfigSources()                           {return java.util.List.of();}

        @Override
        public <T> T unwrap(Class<T> type)                                                                             {throw new UnsupportedOperationException();}

        @Override
        public <T> java.util.Optional<org.eclipse.microprofile.config.spi.Converter<T>> getConverter(Class<T> forType) {return java.util.Optional.empty();}
    }

    static class StubConnector implements Connector {
        private final String id;
        private final String channelType;

        StubConnector(String id, String channelType) {
            this.id          = id;
            this.channelType = channelType;
        }

        StubConnector(String id)                      {this(id, id);}

        @Override
        public String id()                            {return id;}

        @Override
        public boolean send(ConnectorMessage message) {return true;}

        @Override
        public String channelType()                   {return channelType;}
    }

    static class StubResolver implements DestinationResolver {
        private final String channelId;

        StubResolver(String channelId) {this.channelId = channelId;}

        @Override
        public String channelId()      {return channelId;}

        @Override
        public Optional<String> resolve(String userId, String tenancyId) {
            return Optional.of("resolved");
        }
    }

    static class RecordingRegistry implements DeliveryChannelRegistry {
        final LinkedHashMap<String, DeliveryChannelDescriptor> descriptors = new LinkedHashMap<>();
        final LinkedHashMap<String, NotificationDeliverer>     deliverers  = new LinkedHashMap<>();

        @Override
        public void register(DeliveryChannelDescriptor descriptor, NotificationDeliverer deliverer) {
            descriptors.put(descriptor.channelId(), descriptor);
            deliverers.put(descriptor.channelId(), deliverer);
        }

        @Override
        public Optional<DeliveryChannelDescriptor> resolve(String channelId) {
            return Optional.ofNullable(descriptors.get(channelId));
        }

        @Override
        public Optional<NotificationDeliverer> resolveDeliverer(String channelId) {
            return Optional.ofNullable(deliverers.get(channelId));
        }

        @Override
        public Set<DeliveryChannelDescriptor> discover() {
            return Set.copyOf(descriptors.values());
        }
    }

    @Test
    void registers_connectorWithMatchingResolver() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("email");
        var resolver  = new StubResolver("email");

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(resolver), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.descriptors).containsKey("email");
        assertThat(registry.deliverers).containsKey("email");
    }

    @Test
    void registers_connectorWithoutResolver() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("email");

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.descriptors).containsKey("email");
        assertThat(registry.deliverers).containsKey("email");
    }

    @Test
    void skips_connectorWithNullChannelType() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("slack", null);

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.descriptors).isEmpty();
    }

    @Test
    void usesChannelType_notConnectorId() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("twilio-sms", "sms");
        var resolver  = new StubResolver("sms");

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(resolver), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.descriptors).containsKey("sms");
        assertThat(registry.descriptors).doesNotContainKey("twilio-sms");
    }

    @Test
    void duplicateChannelType_throwsAtStartup() {
        var registry = new RecordingRegistry();
        var c1       = new StubConnector("email-sendgrid", "email");
        var c2       = new StubConnector("email-ses", "email");

        var startup = new NotificationBridgeStartup(
                List.of(c1, c2), List.of(), List.of(), registry, EMPTY_CONFIG);

        assertThatThrownBy(startup::registerBridgedChannels)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");
    }

    @Test
    void descriptorDefaults_email() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("email")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("email");
        assertThat(desc.displayName()).isEqualTo("Email");
        assertThat(desc.external()).isTrue();
        assertThat(desc.defaultEnabled()).isFalse();
        assertThat(desc.defaultMinSeverity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(desc.defaultDigestSchedule()).isNull();
        assertThat(desc.guaranteedMinSeverity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void descriptorDefaults_sms() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("twilio-sms", "sms")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("sms");
        assertThat(desc.displayName()).isEqualTo("SMS");
        assertThat(desc.guaranteedMinSeverity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void descriptorDefaults_whatsapp() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("whatsapp")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("whatsapp");
        assertThat(desc.displayName()).isEqualTo("WhatsApp");
        assertThat(desc.guaranteedMinSeverity()).isNull();
    }

    @Test
    void descriptorDefaults_unknownChannelType_usesChannelTypeAsDisplayName() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("custom-channel")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("custom-channel");
        assertThat(desc.displayName()).isEqualTo("custom-channel");
        assertThat(desc.guaranteedMinSeverity()).isNull();
    }

    @Test
    void multipleConnectors_registersAll() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(
                        new StubConnector("email"),
                        new StubConnector("twilio-sms", "sms"),
                        new StubConnector("whatsapp"),
                        new StubConnector("slack", null)),
                List.of(new StubResolver("email"), new StubResolver("sms")),
                List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.descriptors.keySet())
                .containsExactlyInAnyOrder("email", "sms", "whatsapp");
    }

    // --- Config resolver fallback tests ---

    @Test
    void configResolver_fallsBackWhenNoCdiResolver() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("email");
        var config = new StubConfig(java.util.Map.of(
                "casehub.notification.destinations.email.user-1", "user1@example.com"));

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(), List.of(), registry, config);
        startup.registerBridgedChannels();

        assertThat(registry.deliverers).containsKey("email");
        var deliverer = registry.deliverers.get("email");
        var input = new io.casehub.platform.api.notification.NotificationInput(
                "user-1", "tenant-1", "Test", null, "test",
                io.casehub.platform.api.notification.NotificationSeverity.INFO, null,
                new io.casehub.platform.api.notification.NotificationSource("e1", "wi", "w1", "a1"));
        var result = deliverer.deliver(input);
        assertThat(result.success()).isTrue();
    }

    @Test
    void configResolver_cdiResolverTakesPrecedence() {
        var registry    = new RecordingRegistry();
        var connector   = new StubConnector("email");
        var cdiResolver = new StubResolver("email");
        var config = new StubConfig(java.util.Map.of(
                "casehub.notification.destinations.email.user-1", "config@example.com"));

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(cdiResolver), List.of(), registry, config);
        startup.registerBridgedChannels();

        assertThat(registry.deliverers).containsKey("email");
    }

    @Test
    void configResolver_noConfigNoResolver_deliveryFails() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("email");

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        assertThat(registry.deliverers).containsKey("email");
        var deliverer = registry.deliverers.get("email");
        var input = new io.casehub.platform.api.notification.NotificationInput(
                "user-1", "tenant-1", "Test", null, "test",
                io.casehub.platform.api.notification.NotificationSeverity.INFO, null,
                new io.casehub.platform.api.notification.NotificationSource("e1", "wi", "w1", "a1"));
        var result = deliverer.deliver(input);
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("no destination resolver");
    }

    @Test
    void configResolver_dynamicChannelTypeDiscovery() {
        var registry  = new RecordingRegistry();
        var connector = new StubConnector("custom-channel");
        var config = new StubConfig(java.util.Map.of(
                "casehub.notification.destinations.custom-channel.user-1", "custom-dest"));

        var startup = new NotificationBridgeStartup(
                List.of(connector), List.of(), List.of(), registry, config);
        startup.registerBridgedChannels();

        assertThat(registry.deliverers).containsKey("custom-channel");
    }

    @Test
    void slackConnector_registersWithPerTenantScope() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("slack")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("slack");
        assertThat(desc).isNotNull();
        assertThat(desc.destinationScope()).isEqualTo(DestinationScope.PER_TENANT);
    }

    @Test
    void teamsConnector_registersWithPerTenantScope() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("teams")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("teams");
        assertThat(desc).isNotNull();
        assertThat(desc.destinationScope()).isEqualTo(DestinationScope.PER_TENANT);
    }

    @Test
    void emailConnector_registersWithPerUserScope() {
        var registry = new RecordingRegistry();
        var startup = new NotificationBridgeStartup(
                List.of(new StubConnector("email")), List.of(), List.of(), registry, EMPTY_CONFIG);
        startup.registerBridgedChannels();

        var desc = registry.descriptors.get("email");
        assertThat(desc).isNotNull();
        assertThat(desc.destinationScope()).isEqualTo(DestinationScope.PER_USER);
    }


}
