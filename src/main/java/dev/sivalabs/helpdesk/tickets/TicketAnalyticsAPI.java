package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
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

    private static <K> Map<K, Long> toMap(List<TicketRepository.GroupCount<K>> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        TicketRepository.GroupCount::getGroupKey, TicketRepository.GroupCount::getTickets));
    }
}
