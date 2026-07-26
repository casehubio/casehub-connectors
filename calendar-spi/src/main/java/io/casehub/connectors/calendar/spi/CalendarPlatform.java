package io.casehub.connectors.calendar.spi;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;

public interface CalendarPlatform {

    String id();

    List<CalendarInfo> listCalendars();

    List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to);

    CalendarEvent getEvent(String calendarId, String eventId);

    CalendarEvent createEvent(String calendarId, EventDetails details);

    CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details);

    void deleteEvent(String calendarId, String eventId);
}
