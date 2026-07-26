package io.casehub.connectors.calendar.model;

import java.util.List;
import java.util.Objects;

import io.casehub.connectors.calendar.spi.EventTiming;

public record EventDetails(
        String summary, String description, String location,
        EventTiming timing, List<String> attendees) {

    public EventDetails {
        Objects.requireNonNull(timing, "timing");
        attendees = attendees != null ? List.copyOf(attendees) : List.of();
    }
}
