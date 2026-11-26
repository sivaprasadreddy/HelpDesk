package dev.sivalabs.helpdesk.tickets;

import dev.sivalabs.helpdesk.categories.CategoryAPI;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TicketService {
    private final TicketRepository ticketRepository;
    private final CategoryAPI categoryAPI;

    TicketService(TicketRepository ticketRepository, CategoryAPI categoryAPI) {
        this.ticketRepository = ticketRepository;
        this.categoryAPI = categoryAPI;
    }

    /**
     * Creates an OPEN ticket in an active category. Assignment to a staff member is not done here.
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
        return ticketRepository.save(ticket).getId();
    }
}
