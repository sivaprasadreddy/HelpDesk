package dev.sivalabs.helpdesk.tickets;

import org.jspecify.annotations.Nullable;

/**
 * A ticket's assignee changed: by an ADMIN, or by the system when the previous assignee was deactivated. The new
 * assignee is {@code ticket().assignedTo()}, {@code null} when nobody could take it.
 */
public record TicketAssignedEvent(
        TicketSnapshot ticket,
        @Nullable Long previousAssigneeId,
        @Nullable Long actorId) implements TicketEvent {}
