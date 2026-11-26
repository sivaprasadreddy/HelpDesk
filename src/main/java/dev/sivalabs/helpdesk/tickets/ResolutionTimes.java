package dev.sivalabs.helpdesk.tickets;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Average time from creation to resolution of the tickets resolved in a period, overall and per
 * priority. An average is {@code null} when no ticket in its group was resolved.
 */
public record ResolutionTimes(long resolvedCount, @Nullable Duration average, List<ByPriority> byPriority) {

    public record ByPriority(
            TicketPriority priority,
            long resolvedCount,
            @Nullable Duration average) {}
}
