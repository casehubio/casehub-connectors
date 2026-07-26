package io.casehub.connectors.calendar.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import io.casehub.connectors.calendar.model.CalendarEvent;
import io.casehub.connectors.calendar.model.CalendarInfo;
import io.casehub.connectors.calendar.model.EventDetails;
import io.casehub.connectors.calendar.spi.CalendarPlatform;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class GoogleCalendarPlatform implements CalendarPlatform {

    private static final Logger LOG = Logger.getLogger(GoogleCalendarPlatform.class);
    private static final int MAX_PAGES = 20;

    @ConfigProperty(name = "casehub.connectors.calendar.google.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "casehub.connectors.calendar.google.client-secret", defaultValue = "")
    String clientSecret;

    @ConfigProperty(name = "casehub.connectors.calendar.google.refresh-token", defaultValue = "")
    String refreshToken;

    private Calendar calendarService;

    GoogleCalendarPlatform() {}

    GoogleCalendarPlatform(Calendar calendarService) {
        this.calendarService = calendarService;
    }

    @PostConstruct
    void init() {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            LOG.warn("Google Calendar credentials not configured — platform inactive");
            return;
        }
        try {
            var credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
            calendarService = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("casehub-connectors")
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            LOG.errorf(e, "Failed to initialize Google Calendar client");
        }
    }

    @Override
    public String id() {
        return "google";
    }

    @Override
    public List<CalendarInfo> listCalendars() {
        requireClient();
        try {
            CalendarList list = calendarService.calendarList().list().execute();
            if (list.getItems() == null) return List.of();
            return list.getItems().stream()
                    .map(e -> new CalendarInfo(e.getId(), e.getSummary(),
                            e.getDescription(), Boolean.TRUE.equals(e.getPrimary())))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list calendars", e);
        }
    }

    @Override
    public List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to) {
        requireClient();
        List<CalendarEvent> result = new ArrayList<>();
        try {
            String pageToken = null;
            int    page      = 0;
            while (page < MAX_PAGES) {
                Events response = calendarService.events().list(calendarId)
                                                 .setSingleEvents(true)
                                                 .setOrderBy("startTime")
                                                 .setTimeMin(new DateTime(from.toEpochMilli()))
                                                 .setTimeMax(new DateTime(to.toEpochMilli()))
                                                 .setPageToken(pageToken)
                                                 .execute();
                if (response.getItems() != null) {
                    for (var event : response.getItems()) {
                        result.add(GoogleEventMapper.toCalendarEvent(event, calendarId));
                    }
                }
                pageToken = response.getNextPageToken();
                if (pageToken == null) {break;}
                page++;
            }
            if (page >= MAX_PAGES) {
                LOG.warnf("listEvents hit MAX_PAGES (%d) for calendar '%s' — results may be incomplete",
                          MAX_PAGES, calendarId);
            }
        } catch (IOException e) {
            LOG.warnf(e, "listEvents failed mid-pagination for calendar '%s' — returning %d partial results",
                      calendarId, result.size());
        }
        return Collections.unmodifiableList(result);}

    @Override
    public CalendarEvent getEvent(String calendarId, String eventId) {
        requireClient();
        try {
            var event = calendarService.events().get(calendarId, eventId).execute();
            return GoogleEventMapper.toCalendarEvent(event, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get event " + eventId, e);
        }
    }

    @Override
    public CalendarEvent createEvent(String calendarId, EventDetails details) {
        requireClient();
        try {
            var googleEvent = GoogleEventMapper.toGoogleEvent(details);
            var created = calendarService.events().insert(calendarId, googleEvent).execute();
            return GoogleEventMapper.toCalendarEvent(created, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create event", e);
        }
    }

    @Override
    public CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details) {
        requireClient();
        try {
            var googleEvent = GoogleEventMapper.toGoogleEvent(details);
            var updated = calendarService.events().update(calendarId, eventId, googleEvent).execute();
            return GoogleEventMapper.toCalendarEvent(updated, calendarId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update event " + eventId, e);
        }
    }

    @Override
    public void deleteEvent(String calendarId, String eventId) {
        requireClient();
        try {
            calendarService.events().delete(calendarId, eventId).execute();
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete event " + eventId, e);
        }
    }

    boolean isActive() {
        return calendarService != null;
    }

    private void requireClient() {
        if (calendarService == null) {
            throw new IllegalStateException(
                    "Google Calendar client not initialised — check credentials configuration");
        }
    }
}
