package dev.sivalabs.helpdesk.tickets;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * One staff member's current workload (tickets assigned to them now) and performance in a period
 * (tickets they resolved then, and how long those took on average; {@code null} when none).
 */
public record StaffPerformance(
        Long staffId,
        String name,
        boolean active,
        long open,
        long inProgress,
        long resolvedInPeriod,
        @Nullable Duration averageResolutionTime) {

    public long workload() {
        return open + inProgress;
    }
}
