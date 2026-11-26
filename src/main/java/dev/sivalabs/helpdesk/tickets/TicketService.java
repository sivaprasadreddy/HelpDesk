package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.categories.CategoryDto;
import dev.sivalabs.helpdesk.shared.ResourceNotFoundException;
import dev.sivalabs.helpdesk.users.AccessPolicy;
import dev.sivalabs.helpdesk.users.AuthUtils;
import dev.sivalabs.helpdesk.users.SecurityUser;
import dev.sivalabs.helpdesk.users.StaffDeactivatedEvent;
import dev.sivalabs.helpdesk.users.UserAPI;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TicketService {
    private static final Logger LOG = LoggerFactory.getLogger(TicketService.class);

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
    private final CommentRepository commentRepository;
    private final ApplicationEventPublisher events;

    TicketService(
            TicketRepository ticketRepository,
            CategoryAPI categoryAPI,
            UserAPI userAPI,
            AccessPolicy accessPolicy,
            CommentRepository commentRepository,
            ApplicationEventPublisher events) {
        this.events = events;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
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
        var saved = ticketRepository.save(ticket);
        if (saved.getAssignedTo() == null) {
            LOG.warn(
                    "Ticket #{} created by user {} in category {} with no active staff to handle it; left unassigned",
                    saved.getId(),
                    cmd.createdBy(),
                    cmd.categoryId());
        } else {
            LOG.info(
                    "Ticket #{} created by user {} in category {} ({}), assigned to staff {}",
                    saved.getId(),
                    cmd.createdBy(),
                    cmd.categoryId(),
                    cmd.priority(),
                    saved.getAssignedTo());
        }
        events.publishEvent(new TicketCreatedEvent(TicketSnapshot.of(saved), cmd.createdBy()));
        return saved.getId();
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

    /** A page of every ticket matching the filter, sorted as {@link #findTickets}. */
    @Transactional(readOnly = true)
    @PreAuthorize("@accessPolicy.canViewAllTickets(principal)")
    public Page<TicketSummary> findAllTickets(TicketFilter filter, Pageable pageable) {
        return toSummaries(ticketRepository.findAll(filter.toSpecification(), stableSort(pageable)));
    }

    /**
     * The ticket, if the principal may view it ({@code AccessPolicy#canViewTicket}). A ticket the principal
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
                .map(TicketService::categoryLabel)
                .findFirst()
                .orElse("Unknown");
        var names = userAPI.findNamesByIds(Stream.of(t.getCreatedBy(), t.getAssignedTo())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return new TicketDetails(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getCategoryId(),
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

    /**
     * Moves the ticket from {@code expected} to {@code target} along the workflow
     * ({@link TicketStatus#nextStatuses}). ADMIN may move any ticket, STAFF only those assigned to them; others get
     * not-found, as on the details page. Fails when the ticket is no longer in {@code expected} (someone else
     * changed it meanwhile) or the move is not allowed. Records when the ticket was resolved and closed; reopening
     * clears the resolution time.
     */
    @Transactional
    public void changeStatus(Long id, TicketStatus expected, TicketStatus target, Object principal) {
        var t = ticketRepository
                .findById(id)
                .filter(ticket -> accessPolicy.canViewTicket(principal, ticket.getCreatedBy(), ticket.getAssignedTo()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket #" + id + " not found"));
        if (!accessPolicy.canUpdateTicketStatus(principal, t.getAssignedTo())) {
            throw new AccessDeniedException("You cannot change the status of ticket #" + id);
        }
        if (t.getStatus() != expected) {
            LOG.warn(
                    "Status change on ticket #{} refused: user {} saw {} but it is now {}",
                    id,
                    userIdOf(principal),
                    expected,
                    t.getStatus());
            throw new InvalidStatusTransitionException(
                    "Ticket #" + id + " is now " + t.getStatus().getLabel() + "; review it and try again");
        }
        if (!expected.canMoveTo(target)) {
            LOG.warn("Status change on ticket #{} refused: {} -> {} is not allowed", id, expected, target);
            throw new InvalidStatusTransitionException(
                    "A ticket that is " + expected.getLabel() + " cannot be moved to " + target.getLabel());
        }
        var now = Instant.now();
        switch (target) {
            case RESOLVED -> t.setResolvedAt(now);
            case CLOSED -> t.setClosedAt(now);
            case IN_PROGRESS, OPEN -> t.setResolvedAt(null);
        }
        t.setStatus(target);
        LOG.info("Ticket #{} moved {} -> {} by user {}", id, expected, target, userIdOf(principal));
        events.publishEvent(new TicketStatusChangedEvent(TicketSnapshot.of(t), expected, userIdOf(principal)));
    }

    /**
     * Assigns the ticket to an active staff member, whether or not they handle its category. A CLOSED
     * ticket keeps its assignee.
     *
     * @return {@code false} when the ticket was already assigned to that staff member
     */
    @Transactional
    @PreAuthorize("@accessPolicy.canAssignTicket(principal)")
    public boolean assignTicket(Long id, Long staffId) {
        var t = ticketRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket #" + id + " not found"));
        if (t.getStatus() == TicketStatus.CLOSED) {
            LOG.warn("Assignment of closed ticket #{} to staff {} refused", id, staffId);
            throw new InvalidStatusTransitionException("Ticket #" + id + " is closed and cannot be reassigned");
        }
        if (!userAPI.isActiveStaff(staffId)) {
            LOG.warn("Assignment of ticket #{} refused: user {} is not an active staff member", id, staffId);
            throw new InvalidAssigneeException(staffId);
        }
        if (staffId.equals(t.getAssignedTo())) {
            LOG.debug("Ticket #{} already assigned to staff {}; nothing to do", id, staffId);
            return false;
        }
        var previous = t.getAssignedTo();
        t.setAssignedTo(staffId);
        LOG.info(
                "Ticket #{} reassigned from {} to staff {} by user {}",
                id,
                previous == null ? "nobody" : "staff " + previous,
                staffId,
                AuthUtils.getCurrentUserId());
        events.publishEvent(new TicketAssignedEvent(TicketSnapshot.of(t), previous, AuthUtils.getCurrentUserId()));
        return true;
    }

    /**
     * Updates the ticket's title, description, priority and category. The category must be active, or
     * the one the ticket already has (so a ticket in a deactivated category can still be edited). Changing the
     * category does not reassign the ticket.
     */
    @Transactional
    @PreAuthorize("@accessPolicy.canUpdateTicketDetails(principal)")
    public void updateTicket(UpdateTicketCmd cmd) {
        var t = ticketRepository
                .findById(cmd.id())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket #" + cmd.id() + " not found"));
        if (!cmd.categoryId().equals(t.getCategoryId()) && !categoryAPI.isActiveCategory(cmd.categoryId())) {
            LOG.warn("Update of ticket #{} refused: category {} is not active", cmd.id(), cmd.categoryId());
            throw new InvalidTicketCategoryException(cmd.categoryId());
        }
        t.setTitle(cmd.title().trim());
        t.setDescription(cmd.description().trim());
        t.setPriority(cmd.priority());
        t.setCategoryId(cmd.categoryId());
        LOG.info(
                "Ticket #{} details updated by user {} (priority {}, category {})",
                cmd.id(),
                AuthUtils.getCurrentUserId(),
                cmd.priority(),
                cmd.categoryId());
        events.publishEvent(new TicketUpdatedEvent(TicketSnapshot.of(t), AuthUtils.getCurrentUserId()));
    }

    /** The ticket's comments, oldest first, if the principal may view the ticket. */
    @Transactional(readOnly = true)
    public List<CommentView> findComments(Long ticketId, Object principal) {
        var ticket = loadVisible(ticketId, principal);
        var comments = commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
        var names = userAPI.findNamesByIds(
                comments.stream().map(CommentEntity::getAuthorId).collect(Collectors.toSet()));
        return comments.stream()
                .map(c -> new CommentView(
                        c.getId(),
                        names.getOrDefault(c.getAuthorId(), "Unknown"),
                        c.getAuthorId().equals(ticket.getCreatedBy()),
                        c.getContent(),
                        c.getCreatedAt()))
                .toList();
    }

    /**
     * Adds a comment by {@code authorId}. ADMIN may comment on any ticket, STAFF on tickets assigned to
     * them and USER on tickets they raised ({@code AccessPolicy#canComment}); others get not-found, as on the
     * details page. A CLOSED ticket takes no more comments.
     */
    @Transactional
    public void addComment(Long ticketId, Long authorId, String content, Object principal) {
        var ticket = loadVisible(ticketId, principal);
        if (!accessPolicy.canComment(principal, ticket.getCreatedBy(), ticket.getAssignedTo())) {
            throw new AccessDeniedException("You cannot comment on ticket #" + ticketId);
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            LOG.warn("Comment by user {} on closed ticket #{} refused", authorId, ticketId);
            throw new InvalidStatusTransitionException("Ticket #" + ticketId + " is closed and takes no more comments");
        }
        var comment = new CommentEntity();
        comment.setTicketId(ticketId);
        comment.setAuthorId(authorId);
        comment.setContent(content.trim());
        commentRepository.save(comment);
        LOG.info("Comment {} added to ticket #{} by user {}", comment.getId(), ticketId, authorId);
        events.publishEvent(new TicketCommentAddedEvent(TicketSnapshot.of(ticket), comment.getContent(), authorId));
    }

    private static @Nullable Long userIdOf(Object principal) {
        return principal instanceof SecurityUser user ? user.getId() : null;
    }

    private TicketEntity loadVisible(Long id, Object principal) {
        return ticketRepository
                .findById(id)
                .filter(ticket -> accessPolicy.canViewTicket(principal, ticket.getCreatedBy(), ticket.getAssignedTo()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket #" + id + " not found"));
    }

    /**
     * When a staff member is deactivated, each of their OPEN and IN_PROGRESS tickets goes to another
     * active staff member handling its category (the same rule as for new tickets), or is left unassigned for an
     * ADMIN when nobody does. RESOLVED and CLOSED tickets keep the assignee as a record of who did the work.
     * Runs in the deactivating transaction.
     *
     * @return the number of tickets reassigned or unassigned
     */
    @EventListener
    @Transactional
    public int onStaffDeactivated(StaffDeactivatedEvent event) {
        var tickets = ticketRepository.findByAssignedToAndStatusInOrderByIdAsc(event.staffId(), ACTIVE_STATUSES);
        LOG.info("Staff {} deactivated; handing over {} open ticket(s)", event.staffId(), tickets.size());
        for (var t : tickets) {
            var previous = t.getAssignedTo();
            t.setAssignedTo(chooseAssignee(t.getCategoryId()).orElse(null));
            ticketRepository.flush(); // so the next ticket's choice sees this one's workload
            if (t.getAssignedTo() == null) {
                LOG.warn(
                        "Ticket #{} left unassigned: no other active staff handles category {}",
                        t.getId(),
                        t.getCategoryId());
            } else {
                LOG.info("Ticket #{} handed over from staff {} to staff {}", t.getId(), previous, t.getAssignedTo());
            }
            events.publishEvent(new TicketAssignedEvent(TicketSnapshot.of(t), previous, null));
        }
        return tickets.size();
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

    /**
     * Tickets keep a category that is deactivated later (it only stops taking new tickets), labelled so
     * an ADMIN can recategorise them.
     */
    private static String categoryLabel(CategoryDto c) {
        return c.active() ? c.name() : c.name() + " (inactive)";
    }

    /** Resolves category and user names with one lookup each for the whole page. */
    private Page<TicketSummary> toSummaries(Page<TicketEntity> page) {
        var categoryNames = categoryAPI.findAllCategories().stream()
                .collect(Collectors.toMap(CategoryDto::id, TicketService::categoryLabel));
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
        LOG.debug("Choosing assignee for category {} from {} with workload {}", categoryId, candidates, workload);
        return candidates.stream()
                .min(Comparator.<Long>comparingLong(id -> workload.getOrDefault(id, 0L))
                        .thenComparing(Comparator.naturalOrder()));
    }
}
