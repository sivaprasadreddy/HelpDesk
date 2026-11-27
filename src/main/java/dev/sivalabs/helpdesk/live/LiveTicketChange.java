package dev.sivalabs.helpdesk.live;

import org.jspecify.annotations.Nullable;

/**
 * A ticket changed. Who may hear about it is decided from {@code createdBy} and {@code assignedTo}, plus the
 * {@code previousAssigneeId} of a reassignment, so the ticket leaves that person's list. Only {@code ticketId} and
 * {@code kind} are sent to browsers, which then fetch what they may see.
 */
record LiveTicketChange(
        Long ticketId,
        String kind,
        Long createdBy,
        @Nullable Long assignedTo,
        @Nullable Long previousAssigneeId) {}
