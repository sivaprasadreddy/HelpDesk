package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/**
 * Something that happened to a ticket, published after it is saved. {@code actorId} is the user who did it, or
 * {@code null} when the system did (such as reassigning work when a staff member is deactivated).
 */
public sealed interface TicketEvent
        permits TicketCreatedEvent,
                TicketAssignedEvent,
                TicketStatusChangedEvent,
                TicketUpdatedEvent,
                TicketCommentAddedEvent {

    TicketSnapshot ticket();

    @Nullable
    Long actorId();
}
