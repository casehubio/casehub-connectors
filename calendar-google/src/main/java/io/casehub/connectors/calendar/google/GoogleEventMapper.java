package io.casehub.connectors.calendar.google;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;

final class GoogleEventMapper {

    private GoogleEventMapper() {}

    static CalendarEvent toCalendarEvent(Event event, String calendarId) {
        EventTiming timing;
        if (event.getStart().getDateTime() != null) {
            String tz = event.getStart().getTimeZone();
            ZoneId zoneId = tz != null ? ZoneId.of(tz) : ZoneId.of("UTC");
            timing = new EventTiming.Timed(
                    Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                    Instant.ofEpochMilli(event.getEnd().getDateTime().getValue()),
                    zoneId);
        } else {
            timing = new EventTiming.AllDay(
                    LocalDate.parse(event.getStart().getDate().toStringRfc3339()),
                    LocalDate.parse(event.getEnd().getDate().toStringRfc3339()));
        }

        List<String> attendees = event.getAttendees() != null
                ? event.getAttendees().stream().map(EventAttendee::getEmail).toList()
                : List.of();

        return new CalendarEvent(
                event.getId(), calendarId,
                event.getSummary(), event.getDescription(), event.getLocation(),
                timing, attendees, event.getRecurringEventId());
    }

    static Event toGoogleEvent(EventDetails details) {
        Event event = new Event()
                .setSummary(details.summary())
                .setDescription(details.description())
                .setLocation(details.location());

        switch (details.timing()) {
            case EventTiming.Timed t -> {
                event.setStart(new EventDateTime()
                        .setDateTime(new DateTime(t.start().toEpochMilli()))
                        .setTimeZone(t.timeZone().getId()));
                event.setEnd(new EventDateTime()
                        .setDateTime(new DateTime(t.end().toEpochMilli()))
                        .setTimeZone(t.timeZone().getId()));
            }
            case EventTiming.AllDay a -> {
                event.setStart(new EventDateTime()
                        .setDate(new DateTime(a.start().toString())));
                event.setEnd(new EventDateTime()
                        .setDate(new DateTime(a.end().toString())));
            }
        }

        if (details.attendees() != null && !details.attendees().isEmpty()) {
            event.setAttendees(details.attendees().stream()
                    .map(email -> new EventAttendee().setEmail(email))
                    .toList());
        }

        return event;
    }
}
