package io.casehub.connectors.calendar.google;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleEventMapperTest {

    @Test
    void toCalendarEvent_timedEvent() {
        var event = new Event()
                .setId("evt-1")
                .setSummary("Standup")
                .setDescription("Daily sync")
                .setLocation("Room 1")
                .setStart(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T10:00:00+01:00"))
                        .setTimeZone("Europe/London"))
                .setEnd(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T10:30:00+01:00"))
                        .setTimeZone("Europe/London"))
                .setAttendees(List.of(
                        new EventAttendee().setEmail("alice@example.com"),
                        new EventAttendee().setEmail("bob@example.com")));

        var result = GoogleEventMapper.toCalendarEvent(event, "primary");

        assertThat(result.id()).isEqualTo("evt-1");
        assertThat(result.calendarId()).isEqualTo("primary");
        assertThat(result.summary()).isEqualTo("Standup");
        assertThat(result.description()).isEqualTo("Daily sync");
        assertThat(result.location()).isEqualTo("Room 1");
        assertThat(result.timing()).isInstanceOf(EventTiming.Timed.class);
        var timed = (EventTiming.Timed) result.timing();
        assertThat(timed.timeZone()).isEqualTo(ZoneId.of("Europe/London"));
        assertThat(result.attendees()).containsExactly("alice@example.com", "bob@example.com");
        assertThat(result.recurringEventId()).isNull();
    }

    @Test
    void toCalendarEvent_allDayEvent() {
        var event = new Event()
                .setId("evt-2")
                .setSummary("Holiday")
                .setStart(new EventDateTime().setDate(new DateTime("2026-07-27")))
                .setEnd(new EventDateTime().setDate(new DateTime("2026-07-28")));

        var result = GoogleEventMapper.toCalendarEvent(event, "primary");

        assertThat(result.timing()).isInstanceOf(EventTiming.AllDay.class);
        var allDay = (EventTiming.AllDay) result.timing();
        assertThat(allDay.start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(allDay.end()).isEqualTo(LocalDate.of(2026, 7, 28));
    }

    @Test
    void toCalendarEvent_recurringInstance() {
        var event = new Event()
                .setId("evt-3_20260726T100000Z")
                .setRecurringEventId("evt-3")
                .setSummary("Weekly sync")
                .setStart(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T10:00:00Z"))
                        .setTimeZone("UTC"))
                .setEnd(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T11:00:00Z"))
                        .setTimeZone("UTC"));

        var result = GoogleEventMapper.toCalendarEvent(event, "primary");

        assertThat(result.recurringEventId()).isEqualTo("evt-3");
    }

    @Test
    void toCalendarEvent_noAttendees() {
        var event = new Event()
                .setId("evt-4")
                .setSummary("Solo task")
                .setStart(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T14:00:00Z"))
                        .setTimeZone("UTC"))
                .setEnd(new EventDateTime()
                        .setDateTime(DateTime.parseRfc3339("2026-07-26T15:00:00Z"))
                        .setTimeZone("UTC"));

        var result = GoogleEventMapper.toCalendarEvent(event, "cal-1");
        assertThat(result.attendees()).isEmpty();
    }

    @Test
    void toGoogleEvent_timedEvent() {
        var details = new EventDetails("Meeting", "Desc", "Room 2",
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("Europe/London")),
                List.of("alice@example.com"));

        var result = GoogleEventMapper.toGoogleEvent(details);

        assertThat(result.getSummary()).isEqualTo("Meeting");
        assertThat(result.getDescription()).isEqualTo("Desc");
        assertThat(result.getLocation()).isEqualTo("Room 2");
        assertThat(result.getStart().getDateTime()).isNotNull();
        assertThat(result.getStart().getTimeZone()).isEqualTo("Europe/London");
        assertThat(result.getAttendees()).hasSize(1);
        assertThat(result.getAttendees().getFirst().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void toGoogleEvent_allDayEvent() {
        var details = new EventDetails("Holiday", null, null,
                new EventTiming.AllDay(
                        LocalDate.of(2026, 7, 27),
                        LocalDate.of(2026, 7, 28)),
                List.of());

        var result = GoogleEventMapper.toGoogleEvent(details);

        assertThat(result.getStart().getDate()).isNotNull();
        assertThat(result.getStart().getDateTime()).isNull();
        assertThat(result.getEnd().getDate()).isNotNull();
    }
}
