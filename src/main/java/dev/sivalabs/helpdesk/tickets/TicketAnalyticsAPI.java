package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.staff.StaffAPI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The tickets module's facade for analytics (ADMIN only, see {@code AccessPolicy#canViewAnalytics}). */
@Component
public class TicketAnalyticsAPI {
    private final TicketRepository ticketRepository;
    private final CategoryAPI categoryAPI;
    private final StaffAPI staffAPI;

    TicketAnalyticsAPI(TicketRepository ticketRepository, CategoryAPI categoryAPI, StaffAPI staffAPI) {
        this.ticketRepository = ticketRepository;
        this.categoryAPI = categoryAPI;
        this.staffAPI = staffAPI;
    }

    /**
     * Every ticket counted by status and by priority (each in workflow / severity order, zero groups
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
     * Tickets created and resolved on each day from {@code from} to {@code to} inclusive, days without
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
        var start = startOf(from);
        var end = startOf(to.plusDays(1));
        var created = countPerDay(ticketRepository.findCreatedAtBetween(start, end), zone);
        var resolved = countPerDay(ticketRepository.findResolvedAtBetween(start, end), zone);
        return from.datesUntil(to.plusDays(1))
                .map(d -> new DailyTicketFlow(d, created.getOrDefault(d, 0L), resolved.getOrDefault(d, 0L)))
                .toList();
    }

    /**
     * The average time from creation to resolution of the tickets resolved from {@code from} to
     * {@code to} inclusive (server time zone), overall and per priority in severity order. As in
     * {@link #getCreatedVsResolved}, a ticket counts from its last resolution; closing does not change it.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from}
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAnalytics(principal)")
    public ResolutionTimes getResolutionTimes(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        var rows = ticketRepository.findResolutionsBetween(startOf(from), startOf(to.plusDays(1)));
        var byPriority = rows.stream().collect(Collectors.groupingBy(TicketRepository.Resolution::getPriority));
        var perPriority = Arrays.stream(TicketPriority.values())
                .map(p -> {
                    var group = byPriority.getOrDefault(p, List.of());
                    return new ResolutionTimes.ByPriority(p, group.size(), average(group));
                })
                .toList();
        return new ResolutionTimes(rows.size(), average(rows), perPriority);
    }

    /**
     * Per staff member, the OPEN and IN_PROGRESS tickets assigned to them now, and the tickets they
     * resolved from {@code from} to {@code to} inclusive with the average time those took (credited to whoever is
     * assigned, normally the one who resolved it). Lists every active staff member, and deactivated ones who still
     * have tickets or resolved some in the period; busiest first, then by name.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from}
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAnalytics(principal)")
    public List<StaffPerformance> getStaffPerformance(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The period ends before it starts");
        }
        var counts = ticketRepository.countByAssigneeAndStatus(EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS));
        var resolved = ticketRepository.findResolutionsBetween(startOf(from), startOf(to.plusDays(1))).stream()
                .filter(r -> r.getAssignedTo() != null)
                .collect(Collectors.groupingBy(TicketRepository.Resolution::getAssignedTo));
        return staffAPI.findStaffMembers().stream()
                .map(u -> {
                    var open = count(counts, u.id(), TicketStatus.OPEN);
                    var inProgress = count(counts, u.id(), TicketStatus.IN_PROGRESS);
                    var done = resolved.getOrDefault(u.id(), List.of());
                    return new StaffPerformance(
                            u.id(), u.name(), u.active(), open, inProgress, done.size(), average(done));
                })
                .filter(p -> p.active() || p.workload() > 0 || p.resolvedInPeriod() > 0)
                .sorted(Comparator.comparingLong(StaffPerformance::workload)
                        .reversed()
                        .thenComparing(StaffPerformance::name))
                .toList();
    }

    private static long count(List<TicketRepository.AssigneeStatusCount> counts, Long staffId, TicketStatus status) {
        return counts.stream()
                .filter(c -> c.getStaffId().equals(staffId) && c.getStatus() == status)
                .mapToLong(TicketRepository.AssigneeStatusCount::getTickets)
                .sum();
    }

    private static @Nullable Duration average(List<TicketRepository.Resolution> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        var totalSeconds = rows.stream()
                .mapToLong(r ->
                        Duration.between(r.getCreatedAt(), r.getResolvedAt()).toSeconds())
                .sum();
        return Duration.ofSeconds(Math.round((double) totalSeconds / rows.size()));
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant();
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
