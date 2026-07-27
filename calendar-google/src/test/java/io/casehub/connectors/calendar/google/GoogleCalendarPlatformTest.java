package io.casehub.connectors.calendar.google;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.EventTiming;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleCalendarPlatformTest {

    private WireMockServer wireMock;
    private GoogleCalendarPlatform platform;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        Calendar calendarService = new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {})
                .setApplicationName("test")
                .setRootUrl("http://localhost:" + wireMock.port() + "/")
                .build();

        platform = new GoogleCalendarPlatform(calendarService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void id_isGoogle() {
        assertThat(platform.id()).isEqualTo("google");
    }

    @Test
    void listCalendars_returnsMappedCalendars() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/users/me/calendarList"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#calendarList",
                                  "items": [
                                    {
                                      "id": "primary",
                                      "summary": "My Calendar",
                                      "description": "Main calendar",
                                      "primary": true
                                    },
                                    {
                                      "id": "work@example.com",
                                      "summary": "Work",
                                      "description": null,
                                      "primary": false
                                    }
                                  ]
                                }
                                """)));

        var calendars = platform.listCalendars();

        assertThat(calendars).hasSize(2);
        assertThat(calendars.get(0).id()).isEqualTo("primary");
        assertThat(calendars.get(0).summary()).isEqualTo("My Calendar");
        assertThat(calendars.get(0).primary()).isTrue();
        assertThat(calendars.get(1).id()).isEqualTo("work@example.com");
        assertThat(calendars.get(1).primary()).isFalse();
    }

    @Test
    void listEvents_singlePage_returnsMappedEvents() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Standup",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T10:30:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().id()).isEqualTo("evt-1");
        assertThat(events.getFirst().summary()).isEqualTo("Standup");
        assertThat(events.getFirst().timing()).isInstanceOf(EventTiming.Timed.class);
    }

    @Test
    void listEvents_pagination_collectsAllPages() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Page 1",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ],
                                  "nextPageToken": "page2"
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.equalTo("page2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-2",
                                      "summary": "Page 2",
                                      "start": {"dateTime": "2026-07-26T14:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T15:00:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).summary()).isEqualTo("Page 1");
        assertThat(events.get(1).summary()).isEqualTo("Page 2");
    }

    @Test
    void listEvents_midPaginationFailure_returnsPartialResults() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1",
                                      "summary": "Survived",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ],
                                  "nextPageToken": "page2"
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .withQueryParam("pageToken", WireMock.equalTo("page2"))
                .willReturn(aResponse().withStatus(500)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().summary()).isEqualTo("Survived");
    }

    @Test
    void listEvents_allDayEvent_mappedCorrectly() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-allday",
                                      "summary": "Holiday",
                                      "start": {"date": "2026-07-27"},
                                      "end": {"date": "2026-07-28"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-27T00:00:00Z"),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().timing()).isInstanceOf(EventTiming.AllDay.class);
        var allDay = (EventTiming.AllDay) events.getFirst().timing();
        assertThat(allDay.start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(allDay.end()).isEqualTo(LocalDate.of(2026, 7, 28));
    }

    @Test
    void getEvent_returnsMappedEvent() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "evt-1",
                                  "summary": "Found it",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"},
                                  "attendees": [{"email": "alice@example.com"}]
                                }
                                """)));

        var event = platform.getEvent("primary", "evt-1");

        assertThat(event.summary()).isEqualTo("Found it");
        assertThat(event.attendees()).containsExactly("alice@example.com");
    }

    @Test
    void createEvent_sendsAndReturnsMappedEvent() {
        wireMock.stubFor(post(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-evt",
                                  "summary": "Created",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "Europe/London"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "Europe/London"}
                                }
                                """)));

        var details = new EventDetails("Created", null, null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("Europe/London")),
                List.of());

        var event = platform.createEvent("primary", details);

        assertThat(event.id()).isEqualTo("new-evt");
        assertThat(event.summary()).isEqualTo("Created");
    }

    @Test
    void updateEvent_sendsAndReturnsMappedEvent() {
        wireMock.stubFor(put(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "evt-1",
                                  "summary": "Updated",
                                  "description": "New desc",
                                  "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                  "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                }
                                """)));

        var details = new EventDetails("Updated", "New desc", null,
                new EventTiming.Timed(
                        Instant.parse("2026-07-26T10:00:00Z"),
                        Instant.parse("2026-07-26T11:00:00Z"),
                        ZoneId.of("UTC")),
                List.of());

        var event = platform.updateEvent("primary", "evt-1", details);

        assertThat(event.summary()).isEqualTo("Updated");
        assertThat(event.description()).isEqualTo("New desc");
    }

    @Test
    void deleteEvent_callsDeleteEndpoint() {
        wireMock.stubFor(delete(urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1"))
                .willReturn(aResponse().withStatus(204)));

        platform.deleteEvent("primary", "evt-1");

        wireMock.verify(1, WireMock.deleteRequestedFor(
                urlPathEqualTo("/calendar/v3/calendars/primary/events/evt-1")));
    }

    @Test
    void requireClient_noClient_throwsIllegalState() {
        var unconfigured = new GoogleCalendarPlatform();

        assertThatThrownBy(() -> unconfigured.listCalendars())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialised");
    }

    @Test
    void isActive_withClient_true() {
        assertThat(platform.isActive()).isTrue();
    }

    @Test
    void isActive_withoutClient_false() {
        var unconfigured = new GoogleCalendarPlatform();
        assertThat(unconfigured.isActive()).isFalse();
    }

    @Test
    void listEvents_recurringInstance_preservesRecurringEventId() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "kind": "calendar#events",
                                  "items": [
                                    {
                                      "id": "evt-1_20260726",
                                      "recurringEventId": "evt-1",
                                      "summary": "Weekly sync",
                                      "start": {"dateTime": "2026-07-26T10:00:00Z", "timeZone": "UTC"},
                                      "end": {"dateTime": "2026-07-26T11:00:00Z", "timeZone": "UTC"}
                                    }
                                  ]
                                }
                                """)));

        var events = platform.listEvents("primary",
                Instant.parse("2026-07-26T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(events.getFirst().recurringEventId()).isEqualTo("evt-1");
    }

    @Test
    void getEvent_serverError_throwsRuntime() {
        wireMock.stubFor(get(urlPathEqualTo("/calendar/v3/calendars/primary/events/bad"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> platform.getEvent("primary", "bad"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get event");
    }
}
