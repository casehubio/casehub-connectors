package io.casehub.connectors.calendar.ref;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;

@ApplicationScoped
public class InMemoryCalendarBackend implements CalendarBackend {

    private final ConcurrentHashMap<String, List<CalendarEvent>> events = new ConcurrentHashMap<>();

    @Override
    public List<CalendarInfo> listCalendars() {
        return List.of(new CalendarInfo("primary", "Primary", "Default calendar", true));
    }

    @Override
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        return events.getOrDefault(calendarId, List.of()).stream()
                .filter(e -> overlaps(e.timing(), from, to))
                .toList();
    }

    @Override
    public CalendarEvent getEvent(String calendarId, String eventId) {
        return events.getOrDefault(calendarId, List.of()).stream()
                .filter(e -> e.id().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event '" + eventId + "' not found in calendar '" + calendarId + "'"));
    }

    @Override
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        var event = new CalendarEvent(
                UUID.randomUUID().toString(), calendarId,
                details.summary(), details.description(), details.location(),
                details.timing(), details.attendees(), null);
        events.computeIfAbsent(calendarId, k -> new ArrayList<>()).add(event);
        return event;
    }

    @Override
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        var list = events.get(calendarId);
        if (list == null) {
            throw new IllegalArgumentException(
                    "Event '" + eventId + "' not found in calendar '" + calendarId + "'");
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(eventId)) {
                var old = list.get(i);
                var updated = new CalendarEvent(
                        eventId, calendarId,
                        details.summary(), details.description(), details.location(),
                        details.timing(), details.attendees(), old.recurringEventId());
                list.set(i, updated);
                return updated;
            }
        }
        throw new IllegalArgumentException(
                "Event '" + eventId + "' not found in calendar '" + calendarId + "'");
    }

    @Override
    public void deleteEvent(String calendarId, String eventId) {
        var list = events.get(calendarId);
        if (list == null || !list.removeIf(e -> e.id().equals(eventId))) {
            throw new IllegalArgumentException(
                    "Event '" + eventId + "' not found in calendar '" + calendarId + "'");
        }
    }

    private static boolean overlaps(EventTiming timing, Instant from, Instant to) {
        return switch (timing) {
            case EventTiming.Timed t -> t.start().isBefore(to) && t.end().isAfter(from);
            case EventTiming.AllDay a -> {
                Instant aStart = a.start().atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant aEnd = a.end().atStartOfDay(ZoneOffset.UTC).toInstant();
                yield aStart.isBefore(to) && aEnd.isAfter(from);
            }
        };
    }
}
