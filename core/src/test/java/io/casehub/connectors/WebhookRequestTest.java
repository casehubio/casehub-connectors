package io.casehub.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WebhookRequestTest {

    @Test
    void tenancyId_returnsHeaderValueWhenPresent() {
        final WebhookRequest request = new WebhookRequest(
                "", Map.of("x-tenancy-id", List.of("tenant-123")),
                Map.of(), HttpMethod.POST, "http://example.com");
        assertThat(request.tenancyId()).isEqualTo("tenant-123");
    }

    @Test
    void tenancyId_returnsNullWhenHeaderAbsent() {
        final WebhookRequest request = new WebhookRequest(
                "", Map.of(), Map.of(), HttpMethod.POST, "http://example.com");
        assertThat(request.tenancyId()).isNull();
    }
}
