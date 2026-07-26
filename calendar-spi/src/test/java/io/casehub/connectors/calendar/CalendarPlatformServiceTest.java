package io.casehub.connectors.calendar;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarPlatformServiceTest {

    static class StubPlatform implements CalendarPlatform {
        private final String platformId;
        StubPlatform(String id) { this.platformId = id; }
        @Override public String id() { return platformId; }
        @Override public List<CalendarInfo> listCalendars() { return List.of(); }
        @Override public List<CalendarEvent> listEvents(String c, Instant f, Instant t) { return List.of(); }
        @Override public CalendarEvent getEvent(String c, String e) { return null; }
        @Override public CalendarEvent createEvent(String c, EventDetails d) { return null; }
        @Override public CalendarEvent updateEvent(String c, String e, EventDetails d) { return null; }
        @Override public void deleteEvent(String c, String e) {}
    }

    @Test
    void platform_knownId_returnsPlatform() {
        var service = new CalendarPlatformService(List.of(new StubPlatform("google")));
        assertThat(service.platform("google").id()).isEqualTo("google");
    }

    @Test
    void platform_unknownId_throws() {
        var service = new CalendarPlatformService(List.of(new StubPlatform("google")));
        assertThatThrownBy(() -> service.platform("outlook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outlook")
                .hasMessageContaining("google");
    }

    @Test
    void supports_knownId_returnsTrue() {
        var service = new CalendarPlatformService(List.of(new StubPlatform("ref")));
        assertThat(service.supports("ref")).isTrue();
    }

    @Test
    void supports_unknownId_returnsFalse() {
        var service = new CalendarPlatformService(List.of(new StubPlatform("ref")));
        assertThat(service.supports("outlook")).isFalse();
    }

    @Test
    void ids_returnsAllRegistered() {
        var service = new CalendarPlatformService(
                List.of(new StubPlatform("google"), new StubPlatform("ref")));
        assertThat(service.ids()).containsExactlyInAnyOrder("google", "ref");
    }

    @Test
    void duplicateId_throwsAtConstruction() {
        assertThatThrownBy(() -> new CalendarPlatformService(
                List.of(new StubPlatform("ref"), new StubPlatform("ref"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ref");
    }
}
