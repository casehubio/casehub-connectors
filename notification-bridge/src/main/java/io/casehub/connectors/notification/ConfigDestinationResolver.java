package io.casehub.connectors.notification;

import java.util.Map;
import java.util.Optional;

import io.casehub.platform.api.delivery.DestinationResolver;

class ConfigDestinationResolver implements DestinationResolver {

    private final String channelType;
    private final Map<String, String> destinations;

    ConfigDestinationResolver(String channelType, Map<String, String> destinations) {
        this.channelType = channelType;
        this.destinations = Map.copyOf(destinations);
    }

    @Override
    public String channelId() {
        return channelType;
    }

    @Override
    public Optional<String> resolve(String userId, String tenancyId) {
        return Optional.ofNullable(destinations.get(userId));
    }

    boolean hasEntries() {
        return !destinations.isEmpty();
    }
}
