package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.users.UserAPI;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TicketService {
    /** Tickets that still need work count towards a staff member's workload. */
    private static final EnumSet<TicketStatus> ACTIVE_STATUSES =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    /** The properties a ticket list can be sorted by (its sortable column headers). */
    static final Set<String> SORTABLE = Set.of("id", "title", "createdAt", "updatedAt");

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final TicketRepository ticketRepository;
    private final CategoryAPI categoryAPI;
    private final UserAPI userAPI;

    TicketService(TicketRepository ticketRepository, CategoryAPI categoryAPI, UserAPI userAPI) {
        this.ticketRepository = ticketRepository;
        this.categoryAPI = categoryAPI;
        this.userAPI = userAPI;
    }

    /**
     * Creates an OPEN ticket in an active category and assigns it automatically (see {@link #chooseAssignee}).
     *
     * @return the id of the new ticket
     */
    @Transactional
    @PreAuthorize("@accessPolicy.canCreateTicket(principal)")
    public Long createTicket(CreateTicketCmd cmd) {
        if (!categoryAPI.isActiveCategory(cmd.categoryId())) {
            throw new InvalidTicketCategoryException(cmd.categoryId());
        }
        var ticket = new TicketEntity();
        ticket.setTitle(cmd.title().trim());
        ticket.setDescription(cmd.description().trim());
        ticket.setPriority(cmd.priority());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCategoryId(cmd.categoryId());
        ticket.setCreatedBy(cmd.createdBy());
        ticket.setAssignedTo(chooseAssignee(cmd.categoryId()).orElse(null));
        return ticketRepository.save(ticket).getId();
    }

    /**
     * A page of the tickets the user raised, optionally only those in one status. Sorts by the columns the list
     * offers ({@link #SORTABLE}), newest first otherwise, with the id last for a stable order across pages.
     */
    @Transactional(readOnly = true)
    public Page<TicketSummary> findTicketsCreatedBy(Long userId, @Nullable TicketStatus status, Pageable pageable) {
        var stable = stableSort(pageable);
        var page = status == null
                ? ticketRepository.findByCreatedBy(userId, stable)
                : ticketRepository.findByCreatedByAndStatus(userId, status, stable);
        return toSummaries(page);
    }

    /** Number of tickets per status raised by the user, every status present (zero when none). */
    @Transactional(readOnly = true)
    public Map<TicketStatus, Long> countByStatusCreatedBy(Long userId) {
        var counts = new EnumMap<TicketStatus, Long>(TicketStatus.class);
        for (var s : TicketStatus.values()) {
            counts.put(s, 0L);
        }
        ticketRepository.countByStatusForCreator(userId).forEach(c -> counts.put(c.getStatus(), c.getTickets()));
        return counts;
    }

    private static Pageable stableSort(Pageable pageable) {
        var requested = pageable.getSort();
        var allowed = requested.isSorted() && requested.stream().allMatch(o -> SORTABLE.contains(o.getProperty()));
        var sort = allowed ? requested : DEFAULT_SORT;
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(Sort.Order.desc("id"))));
    }

    /** Resolves category and assignee names with one lookup each for the whole page. */
    private Page<TicketSummary> toSummaries(Page<TicketEntity> page) {
        var categoryNames =
                categoryAPI.findAllCategories().stream().collect(Collectors.toMap(CategoryDto::id, CategoryDto::name));
        var assigneeIds = page.stream()
                .map(TicketEntity::getAssignedTo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var assigneeNames = userAPI.findNamesByIds(assigneeIds);
        return page.map(t -> new TicketSummary(
                t.getId(),
                t.getTitle(),
                categoryNames.getOrDefault(t.getCategoryId(), "Unknown"),
                t.getPriority(),
                t.getStatus(),
                t.getAssignedTo() == null ? null : assigneeNames.get(t.getAssignedTo()),
                t.getCreatedAt(),
                t.getUpdatedAt() == null ? t.getCreatedAt() : t.getUpdatedAt()));
    }

    /**
     * The active staff member handling the category with the fewest OPEN and IN_PROGRESS tickets; on a tie, the
     * one with the lowest id. Empty when nobody active handles the category: the ticket stays unassigned until
     * an ADMIN assigns it.
     */
    private Optional<Long> chooseAssignee(Long categoryId) {
        var candidates = userAPI.findActiveStaffIdsHandling(categoryId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        var workload = ticketRepository.countByAssignee(candidates, ACTIVE_STATUSES).stream()
                .collect(Collectors.toMap(
                        TicketRepository.StaffWorkload::getStaffId, TicketRepository.StaffWorkload::getTickets));
        return candidates.stream()
                .min(Comparator.<Long>comparingLong(id -> workload.getOrDefault(id, 0L))
                        .thenComparing(Comparator.naturalOrder()));
    }
}
