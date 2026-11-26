package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/** A ticket was raised (and, when someone handles its category, assigned automatically). */
public record TicketCreatedEvent(
        TicketSnapshot ticket, @Nullable Long actorId) implements TicketEvent {}
