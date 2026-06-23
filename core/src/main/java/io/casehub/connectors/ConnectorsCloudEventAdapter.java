package io.casehub.connectors;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

@ApplicationScoped
public class ConnectorsCloudEventAdapter {

    private static final Logger LOG = Logger.getLogger(ConnectorsCloudEventAdapter.class);
    private static final String TYPE_PREFIX = "io.casehub.connectors.inbound.";

    private final Event<CloudEvent> cloudEventBus;
    private final ObjectMapper objectMapper;

    @Inject
    public ConnectorsCloudEventAdapter(Event<CloudEvent> cloudEventBus, ObjectMapper objectMapper) {
        this.cloudEventBus = cloudEventBus;
        this.objectMapper = objectMapper;
    }

    public void onMessage(@ObservesAsync InboundMessage message) {
        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            LOG.warnf("Failed to serialise InboundMessage for CloudEvent — connector=%s: %s",
                    message.connectorId(), e.getMessage());
            data = new byte[0];
        }

        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(TYPE_PREFIX + message.connectorType())
                .withSource(URI.create("/casehub-connectors/" + message.connectorId()))
                .withSubject("channel/" + message.externalChannelRef())
                .withTime(message.receivedAt().atOffset(ZoneOffset.UTC))
                .withData("application/json", data);

        if (message.tenancyId() != null) {
            builder = builder.withExtension("tenancyid", message.tenancyId());
        }

        cloudEventBus.fireAsync(builder.build())
                .exceptionally(ex -> {
                    LOG.warnf(ex, "CloudEvent dispatch failed for connector=%s",
                            message.connectorId());
                    return null;
                });
    }
}
