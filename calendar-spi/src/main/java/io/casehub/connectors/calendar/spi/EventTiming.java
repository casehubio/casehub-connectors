package io.casehub.connectors.calendar.spi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public sealed interface EventTiming {

    record Timed(Instant start, Instant end, ZoneId timeZone) implements EventTiming {
        public Timed {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(timeZone, "timeZone");
        }
    }

    record AllDay(LocalDate start, LocalDate end) implements EventTiming {
        public AllDay {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }
}
