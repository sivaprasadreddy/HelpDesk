package dev.sivalabs.helpdesk.tickets;

import org.springframework.stereotype.Component;

/** Ticket operations for other modules. Each enforces the same access rules as the web pages. */
@Component
public class TicketAPI {
    private final TicketService ticketService;

    TicketAPI(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Creates a ticket raised by {@code createdBy}, assigned automatically as from the New Ticket page. Only ADMIN
     * and USER may create tickets.
     *
     * @return the id of the new ticket
     * @throws IllegalArgumentException when the category is not an active one
     */
    public Long createTicket(
            String title, String description, TicketPriority priority, Long categoryId, Long createdBy) {
        try {
            return ticketService.createTicket(new CreateTicketCmd(title, description, priority, categoryId, createdBy));
        } catch (InvalidTicketCategoryException e) {
            throw new IllegalArgumentException("Category " + categoryId + " is not an active category", e);
        }
    }
}
