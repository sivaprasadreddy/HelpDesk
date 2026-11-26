package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.UserAPI;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final AccessPolicy accessPolicy;

    TicketService(
            TicketRepository ticketRepository, CategoryAPI categoryAPI, UserAPI userAPI, AccessPolicy accessPolicy) {
        this.ticketRepository = ticketRepository;
        this.categoryAPI = categoryAPI;
        this.userAPI = userAPI;
        this.accessPolicy = accessPolicy;
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
     * A page of the tickets the user raised or is assigned, optionally only those in one status. Sorts by the
     * columns the list offers ({@link #SORTABLE}), newest first otherwise, with the id last for a stable order
     * across pages.
     */
    @Transactional(readOnly = true)
    public Page<TicketSummary> findTickets(
            TicketScope scope, Long userId, @Nullable TicketStatus status, Pageable pageable) {
        var stable = stableSort(pageable);
        var page = switch (scope) {
            case RAISED_BY ->
                status == null
                        ? ticketRepository.findByCreatedBy(userId, stable)
                        : ticketRepository.findByCreatedByAndStatus(userId, status, stable);
            case ASSIGNED_TO ->
                status == null
                        ? ticketRepository.findByAssignedTo(userId, stable)
                        : ticketRepository.findByAssignedToAndStatus(userId, status, stable);
        };
        return toSummaries(page);
    }

    /** REQ-016: a page of every ticket matching the filter, sorted as {@link #findTickets}. */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAllTickets(principal)")
    public Page<TicketSummary> findAllTickets(TicketFilter filter, Pageable pageable) {
        return toSummaries(ticketRepository.findAll(filter.toSpecification(), stableSort(pageable)));
    }

    /**
     * REQ-017: the ticket, if the principal may view it ({@code AccessPolicy#canViewTicket}). A ticket the principal
     * may not view is reported as not found, so its existence does not leak.
     */
    @Transactional(readOnly = true)
    public TicketDetails getTicket(Long id, Object principal) {
        var t = ticketRepository
                .findById(id)
                .filter(ticket -> accessPolicy.canViewTicket(principal, ticket.getCreatedBy(), ticket.getAssignedTo()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket #" + id + " not found"));
        var category = categoryAPI.findAllCategories().stream()
                .filter(c -> c.id().equals(t.getCategoryId()))
                .map(CategoryDto::name)
                .findFirst()
                .orElse("Unknown");
        var names = userAPI.findNamesByIds(Stream.of(t.getCreatedBy(), t.getAssignedTo())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return new TicketDetails(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                category,
                t.getPriority(),
                t.getStatus(),
                t.getCreatedBy(),
                names.getOrDefault(t.getCreatedBy(), "Unknown"),
                t.getAssignedTo(),
                t.getAssignedTo() == null ? null : names.get(t.getAssignedTo()),
                t.getCreatedAt(),
                t.getUpdatedAt() == null ? t.getCreatedAt() : t.getUpdatedAt(),
                t.getResolvedAt(),
                t.getClosedAt());
    }

    /** Whether any ticket exists at all. */
    @Transactional(readOnly = true)
    public boolean hasAnyTickets() {
        return ticketRepository.count() > 0;
    }

    /** Number of tickets per status in the user's list, every status present (zero when none). */
    @Transactional(readOnly = true)
    public Map<TicketStatus, Long> countByStatus(TicketScope scope, Long userId) {
        var counts = new EnumMap<TicketStatus, Long>(TicketStatus.class);
        for (var s : TicketStatus.values()) {
            counts.put(s, 0L);
        }
        var rows = switch (scope) {
            case RAISED_BY -> ticketRepository.countByStatusForCreator(userId);
            case ASSIGNED_TO -> ticketRepository.countByStatusForAssignee(userId);
        };
        rows.forEach(c -> counts.put(c.getStatus(), c.getTickets()));
        return counts;
    }

    private static Pageable stableSort(Pageable pageable) {
        var requested = pageable.getSort();
        var allowed = requested.isSorted() && requested.stream().allMatch(o -> SORTABLE.contains(o.getProperty()));
        var sort = allowed ? requested : DEFAULT_SORT;
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(Sort.Order.desc("id"))));
    }

    /** Resolves category and user names with one lookup each for the whole page. */
    private Page<TicketSummary> toSummaries(Page<TicketEntity> page) {
        var categoryNames =
                categoryAPI.findAllCategories().stream().collect(Collectors.toMap(CategoryDto::id, CategoryDto::name));
        var userIds = page.stream()
                .flatMap(t -> Stream.of(t.getCreatedBy(), t.getAssignedTo()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var userNames = userAPI.findNamesByIds(userIds);
        return page.map(t -> new TicketSummary(
                t.getId(),
                t.getTitle(),
                categoryNames.getOrDefault(t.getCategoryId(), "Unknown"),
                t.getPriority(),
                t.getStatus(),
                userNames.getOrDefault(t.getCreatedBy(), "Unknown"),
                t.getAssignedTo() == null ? null : userNames.get(t.getAssignedTo()),
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
