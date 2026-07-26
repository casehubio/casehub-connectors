package io.casehub.connectors.mcp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.smallrye.common.annotation.Blocking;

import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import io.casehub.connectors.calendar.spi.EventTiming;

@ApplicationScoped
public class CalendarMcpTool {

    private final CalendarPlatformService platformService;

    @Inject
    public CalendarMcpTool(CalendarPlatformService platformService) {
        this.platformService = platformService;
    }

    @Tool(description = "List available calendars")
    @Blocking
    public String listCalendars(String platform) {
        try {
            var calendars = platformService.platform(platform).listCalendars();
            var sb = new StringBuilder();
            for (var cal : calendars) {
                sb.append(cal.id()).append(" — ").append(cal.summary());
                if (cal.primary()) sb.append(" (primary)");
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(description = "List calendar events in a time range")
    @Blocking
    public String listCalendarEvents(String platform, String calendarId,
                                      String from, String to) {
        try {
            String cal = calendarId != null && !calendarId.isBlank() ? calendarId : "primary";
            var events = platformService.platform(platform).listEvents(cal,
                    Instant.parse(from), Instant.parse(to));
            if (events.isEmpty()) return "No events found.";
            var sb = new StringBuilder();
            for (var event : events) {
                appendEvent(sb, event);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(description = "Get a specific calendar event by ID")
    @Blocking
    public String getCalendarEvent(String platform, String calendarId, String eventId) {
        try {
            String cal = calendarId != null && !calendarId.isBlank() ? calendarId : "primary";
            var event = platformService.platform(platform).getEvent(cal, eventId);
            var sb = new StringBuilder();
            appendEvent(sb, event);
            return sb.toString().trim();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(description = "Create a calendar event")
    @Blocking
    public String createCalendarEvent(String platform, String calendarId,
                                       String summary, String description, String location,
                                       String start, String end, String timeZone,
                                       String startDate, String endDate, String attendees) {
        try {
            String cal = calendarId != null && !calendarId.isBlank() ? calendarId : "primary";
            EventTiming timing = parseTiming(start, end, timeZone, startDate, endDate, true);
            List<String> attendeeList = parseAttendees(attendees);

            var details = new EventDetails(summary, description, location, timing, attendeeList);
            var event = platformService.platform(platform).createEvent(cal, details);
            var sb = new StringBuilder("Created event:\n");
            appendEvent(sb, event);
            return sb.toString().trim();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(description = "Update a calendar event")
    @Blocking
    public String updateCalendarEvent(String platform, String calendarId, String eventId,
                                       String summary, String description, String location,
                                       String start, String end, String timeZone,
                                       String startDate, String endDate, String attendees) {
        try {
            String cal = calendarId != null && !calendarId.isBlank() ? calendarId : "primary";
            boolean hasAnyField = summary != null || description != null || location != null
                    || start != null || end != null || timeZone != null
                    || startDate != null || endDate != null || attendees != null;
            if (!hasAnyField) {
                return "Failed: at least one field must be provided for update";
            }

            CalendarPlatform p = platformService.platform(platform);
            var existing = p.getEvent(cal, eventId);

            EventTiming timing;
            boolean hasTiming = start != null || startDate != null;
            if (hasTiming) {
                timing = parseTiming(start, end, timeZone, startDate, endDate, false);
            } else {
                timing = existing.timing();
            }

            var details = new EventDetails(
                    summary != null ? summary : existing.summary(),
                    description != null ? description : existing.description(),
                    location != null ? location : existing.location(),
                    timing,
                    attendees != null ? parseAttendees(attendees) : existing.attendees());

            var updated = p.updateEvent(cal, eventId, details);
            var sb = new StringBuilder("Updated event:\n");
            appendEvent(sb, updated);
            return sb.toString().trim();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(description = "Delete a calendar event")
    @Blocking
    public String deleteCalendarEvent(String platform, String calendarId, String eventId) {
        try {
            String cal = calendarId != null && !calendarId.isBlank() ? calendarId : "primary";
            platformService.platform(platform).deleteEvent(cal, eventId);
            return "Deleted event " + eventId;
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    private static EventTiming parseTiming(String start, String end, String timeZone,
                                            String startDate, String endDate, boolean required) {
        boolean hasTimed = start != null;
        boolean hasAllDay = startDate != null;

        if (hasTimed && hasAllDay) {
            throw new IllegalArgumentException(
                    "provide either start/end/timeZone (timed) or startDate/endDate (all-day), not both");
        }
        if (!hasTimed && !hasAllDay) {
            if (required) {
                throw new IllegalArgumentException(
                        "timing is required — provide start/end/timeZone or startDate/endDate");
            }
            return null;
        }
        if (hasTimed) {
            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException(
                        "timeZone is required for timed events (e.g. 'Europe/London', 'America/New_York')");
            }
            if (end == null || end.isBlank()) {
                throw new IllegalArgumentException("end is required when start is provided");
            }
            return new EventTiming.Timed(Instant.parse(start), Instant.parse(end), ZoneId.of(timeZone));
        }
        if (endDate == null || endDate.isBlank()) {
            throw new IllegalArgumentException("endDate is required when startDate is provided");
        }
        return new EventTiming.AllDay(LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    private static List<String> parseAttendees(String attendees) {
        if (attendees == null || attendees.isBlank()) return List.of();
        return Arrays.stream(attendees.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void appendEvent(StringBuilder sb, CalendarEvent event) {
        sb.append("• ").append(event.summary() != null ? event.summary() : "(no title)");
        sb.append(" (id=").append(event.id()).append(")");
        switch (event.timing()) {
            case EventTiming.Timed t ->
                    sb.append("\n  ").append(t.start()).append(" — ").append(t.end())
                      .append(" (").append(t.timeZone()).append(")");
            case EventTiming.AllDay a ->
                    sb.append("\n  All day: ").append(a.start()).append(" — ").append(a.end());
        }
        if (event.location() != null) sb.append("\n  Location: ").append(event.location());
        if (event.description() != null) sb.append("\n  ").append(event.description());
        if (!event.attendees().isEmpty()) sb.append("\n  Attendees: ").append(String.join(", ", event.attendees()));
        if (event.recurringEventId() != null) sb.append("\n  Recurring series: ").append(event.recurringEventId());
        sb.append("\n");
    }
}
