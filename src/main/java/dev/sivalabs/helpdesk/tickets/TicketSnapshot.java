package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/** The ticket as it was when an event happened, so listeners need not read it back. */
public record TicketSnapshot(
        Long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        Long createdBy,
        @Nullable Long assignedTo) {

    static TicketSnapshot of(TicketEntity t) {
        return new TicketSnapshot(
                t.getId(), t.getTitle(), t.getStatus(), t.getPriority(), t.getCreatedBy(), t.getAssignedTo());
    }
}
