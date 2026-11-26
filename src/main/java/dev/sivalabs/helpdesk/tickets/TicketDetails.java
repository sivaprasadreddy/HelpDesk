package dev.sivalabs.helpdesk.tickets;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One ticket with the category's, creator's and assignee's names resolved, for the details page. */
public record TicketDetails(
        Long id,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        TicketPriority priority,
        TicketStatus status,
        Long createdBy,
        String creatorName,
        @Nullable Long assignedTo,
        @Nullable String assigneeName,
        Instant createdAt,
        Instant lastUpdatedAt,
        @Nullable Instant resolvedAt,
        @Nullable Instant closedAt) {}
