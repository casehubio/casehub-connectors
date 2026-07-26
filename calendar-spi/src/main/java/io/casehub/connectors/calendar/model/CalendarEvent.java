package io.casehub.connectors.calendar.model;

import java.util.List;
import java.util.Objects;

import io.casehub.connectors.calendar.spi.EventTiming;

public record CalendarEvent(
        String id, String calendarId, String summary, String description,
        String location, EventTiming timing,
        List<String> attendees, String recurringEventId) {

    public CalendarEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(calendarId, "calendarId");
        Objects.requireNonNull(timing, "timing");
        attendees = attendees != null ? List.copyOf(attendees) : List.of();
    }
}
