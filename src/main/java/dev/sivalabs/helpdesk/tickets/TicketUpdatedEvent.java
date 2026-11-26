package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/** An ADMIN edited a ticket's title, description, priority or category. */
public record TicketUpdatedEvent(
        TicketSnapshot ticket, @Nullable Long actorId) implements TicketEvent {}
