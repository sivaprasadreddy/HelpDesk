package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import dev.sivalabs.helpdesk.users.UserAPI;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TicketService {
    /** Tickets that still need work count towards a staff member's workload. */
    private static final EnumSet<TicketStatus> ACTIVE_STATUSES =
            EnumSet.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

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
