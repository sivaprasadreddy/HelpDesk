package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The tickets module's facade for analytics (ADMIN only, see {@code AccessPolicy#canViewAnalytics}). */
@Component
public class TicketAnalyticsAPI {
    private final TicketRepository ticketRepository;
    private final CategoryAPI categoryAPI;

    TicketAnalyticsAPI(TicketRepository ticketRepository, CategoryAPI categoryAPI) {
        this.ticketRepository = ticketRepository;
        this.categoryAPI = categoryAPI;
    }

    /**
     * REQ-023: every ticket counted by status and by priority (each in workflow / severity order, zero groups
     * included) and by category (active categories, zero included, then deactivated ones that still have tickets,
     * labelled inactive; ordered by count, then name).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAnalytics(principal)")
    public TicketBreakdowns getBreakdowns() {
        var byStatus = toMap(ticketRepository.countGroupedByStatus());
        var byPriority = toMap(ticketRepository.countGroupedByPriority());
        var byCategory = toMap(ticketRepository.countGroupedByCategory());

        var statuses = Arrays.stream(TicketStatus.values())
                .map(s -> new CountByGroup(s.name(), s.getLabel(), byStatus.getOrDefault(s, 0L)))
                .toList();
        var priorities = Arrays.stream(TicketPriority.values())
                .map(p -> new CountByGroup(p.name(), p.getLabel(), byPriority.getOrDefault(p, 0L)))
                .toList();
        List<CountByGroup> categories = categoryAPI.findAllCategories().stream()
                .filter(c -> c.active() || byCategory.containsKey(c.id()))
                .map(c -> new CountByGroup(
                        c.id().toString(),
                        c.active() ? c.name() : c.name() + " (inactive)",
                        byCategory.getOrDefault(c.id(), 0L)))
                .sorted(Comparator.comparingLong(CountByGroup::count).reversed().thenComparing(CountByGroup::label))
                .toList();
        var total = statuses.stream().mapToLong(CountByGroup::count).sum();
        return new TicketBreakdowns(total, statuses, priorities, categories);
    }

    /** The longest period {@link #getCreatedVsResolved} reports on, in days. */
    public static final int MAX_DAYS = 366;

    /**
     * REQ-024: tickets created and resolved on each day from {@code from} to {@code to} inclusive, days without
     * either included as zeros. Days are in the server's time zone, the one dates are shown in. A ticket counts
     * as resolved on the day it was last resolved; one reopened and not yet resolved again does not count.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from} or the period exceeds {@link #MAX_DAYS}
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAnalytics(principal)")
    public List<DailyTicketFlow> getCreatedVsResolved(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        var days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException("The period must be at most " + MAX_DAYS + " days");
        }
        var zone = ZoneId.systemDefault();
        var start = from.atStartOfDay(zone).toInstant();
        var end = to.plusDays(1).atStartOfDay(zone).toInstant();
        var created = countPerDay(ticketRepository.findCreatedAtBetween(start, end), zone);
        var resolved = countPerDay(ticketRepository.findResolvedAtBetween(start, end), zone);
        return from.datesUntil(to.plusDays(1))
                .map(d -> new DailyTicketFlow(d, created.getOrDefault(d, 0L), resolved.getOrDefault(d, 0L)))
                .toList();
    }

    private static Map<LocalDate, Long> countPerDay(List<Instant> instants, ZoneId zone) {
        return instants.stream()
                .collect(Collectors.groupingBy(i -> LocalDate.ofInstant(i, zone), Collectors.counting()));
    }

    private static <K> Map<K, Long> toMap(List<TicketRepository.GroupCount<K>> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        TicketRepository.GroupCount::getGroupKey, TicketRepository.GroupCount::getTickets));
    }
}
