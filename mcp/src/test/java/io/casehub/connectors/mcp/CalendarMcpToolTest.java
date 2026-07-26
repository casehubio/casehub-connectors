package io.casehub.connectors.mcp;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.ref.InMemoryCalendarBackend;
import io.casehub.connectors.calendar.ref.RefCalendarPlatform;
import io.casehub.connectors.calendar.spi.EventTiming;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarMcpToolTest {

    private CalendarMcpTool tool;
    private RefCalendarPlatform refPlatform;

    @BeforeEach
    void setUp() {
        var backend = new InMemoryCalendarBackend();
        refPlatform = new RefCalendarPlatform(backend);
        var service = new CalendarPlatformService(List.of(refPlatform));
        tool = new CalendarMcpTool(service);
    }

    @Test
    void listCalendars_returnsRefCalendars() {
        var result = tool.listCalendars("ref");
        assertThat(result).contains("primary");
    }

    @Test
    void listCalendars_unknownPlatform() {
        var result = tool.listCalendars("nonexistent");
        assertThat(result).startsWith("Failed:");
    }

    @Test
    void createEvent_timedEvent() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Standup", "Daily sync", "Room 1",
                "2026-07-26T10:00:00Z", "2026-07-26T10:30:00Z", "Europe/London",
                null, null, "alice@example.com");
        assertThat(result).contains("Standup");
        assertThat(result).doesNotStartWith("Failed:");
    }

    @Test
    void createEvent_allDayEvent() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Holiday", null, null,
                null, null, null,
                "2026-07-27", "2026-07-28", null);
        assertThat(result).contains("Holiday");
        assertThat(result).doesNotStartWith("Failed:");
    }

    @Test
    void createEvent_bothTimedAndAllDay_fails() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Bad", null, null,
                "2026-07-26T10:00:00Z", "2026-07-26T11:00:00Z", "UTC",
                "2026-07-27", "2026-07-28", null);
        assertThat(result).startsWith("Failed:");
        assertThat(result).contains("not both");
    }

    @Test
    void createEvent_startWithoutTimeZone_fails() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Bad", null, null,
                "2026-07-26T10:00:00Z", "2026-07-26T11:00:00Z", null,
                null, null, null);
        assertThat(result).startsWith("Failed:");
        assertThat(result).contains("timeZone");
    }

    @Test
    void createEvent_startWithoutEnd_fails() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Bad", null, null,
                "2026-07-26T10:00:00Z", null, "UTC",
                null, null, null);
        assertThat(result).startsWith("Failed:");
        assertThat(result).contains("end is required");
    }

    @Test
    void createEvent_noTiming_fails() {
        var result = tool.createCalendarEvent("ref", "primary",
                "Bad", null, null,
                null, null, null,
                null, null, null);
        assertThat(result).startsWith("Failed:");
        assertThat(result).contains("timing is required");
    }

    @Test
    void createEvent_defaultCalendarId() {
        var result = tool.createCalendarEvent("ref", null,
                "Test", null, null,
                "2026-07-26T10:00:00Z", "2026-07-26T11:00:00Z", "UTC",
                null, null, null);
        assertThat(result).doesNotStartWith("Failed:");
    }

    @Test
    void listEvents_returnsCreated() {
        tool.createCalendarEvent("ref", "primary",
                "Find me", null, null,
                "2026-07-26T10:00:00Z", "2026-07-26T11:00:00Z", "UTC",
                null, null, null);

        var result = tool.listCalendarEvents("ref", "primary",
                "2026-07-26T00:00:00Z", "2026-07-27T00:00:00Z");
        assertThat(result).contains("Find me");
    }

    @Test
    void getEvent_returnsDetails() {
        refPlatform.createEvent("primary", new EventDetails("Get me", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of()));
        var events = refPlatform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));
        var eventId = events.getFirst().id();

        var result = tool.getCalendarEvent("ref", "primary", eventId);
        assertThat(result).contains("Get me");
    }

    @Test
    void deleteEvent_removesEvent() {
        refPlatform.createEvent("primary", new EventDetails("Delete me", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of()));
        var events = refPlatform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));
        var eventId = events.getFirst().id();

        var result = tool.deleteCalendarEvent("ref", "primary", eventId);
        assertThat(result).contains("Deleted");

        assertThat(refPlatform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"))).isEmpty();
    }

    @Test
    void updateEvent_patchMerge() {
        refPlatform.createEvent("primary", new EventDetails("Original", "desc", "Room 1",
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of()));
        var events = refPlatform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));
        var eventId = events.getFirst().id();

        var result = tool.updateCalendarEvent("ref", "primary", eventId,
                "Updated Title", null, null,
                null, null, null,
                null, null, null);
        assertThat(result).contains("Updated Title");
        assertThat(result).doesNotStartWith("Failed:");

        var updated = refPlatform.getEvent("primary", eventId);
        assertThat(updated.summary()).isEqualTo("Updated Title");
        assertThat(updated.description()).isEqualTo("desc");
        assertThat(updated.location()).isEqualTo("Room 1");
    }

    @Test
    void updateEvent_allFieldsNull_fails() {
        var result = tool.updateCalendarEvent("ref", "primary", "evt-1",
                null, null, null,
                null, null, null,
                null, null, null);
        assertThat(result).startsWith("Failed:");
        assertThat(result).contains("at least one field");
    }
}
