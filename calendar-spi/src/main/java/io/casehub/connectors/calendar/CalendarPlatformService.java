package io.casehub.connectors.calendar;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.All;

import io.casehub.connectors.calendar.spi.CalendarPlatform;

@ApplicationScoped
public class CalendarPlatformService {

    private final Map<String, CalendarPlatform> registry;

    public CalendarPlatformService(@All final List<CalendarPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        CalendarPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate calendar platform id: '" + a.id() + "'");
                        }));
    }

    public CalendarPlatform platform(final String id) {
        final CalendarPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No calendar platform registered for id '" + id
                    + "'. Available: " + registry.keySet());
        }
        return platform;
    }

    public boolean supports(final String id) {
        return registry.containsKey(id);
    }

    public Set<String> ids() {
        return Set.copyOf(registry.keySet());
    }
}
