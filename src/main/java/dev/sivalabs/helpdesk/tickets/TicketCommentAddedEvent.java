package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/** Someone commented on a ticket. */
public record TicketCommentAddedEvent(
        TicketSnapshot ticket, String comment, @Nullable Long actorId) implements TicketEvent {}
