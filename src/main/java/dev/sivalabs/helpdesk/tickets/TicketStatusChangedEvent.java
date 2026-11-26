package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/** A ticket moved from {@code previousStatus} to {@code ticket().status()}. */
public record TicketStatusChangedEvent(
        TicketSnapshot ticket,
        TicketStatus previousStatus,
        @Nullable Long actorId) implements TicketEvent {}
