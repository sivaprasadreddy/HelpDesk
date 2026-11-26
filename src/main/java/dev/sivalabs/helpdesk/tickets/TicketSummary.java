package dev.sivalabs.helpdesk.tickets;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A row of a ticket list, with the category's and assignee's names resolved. */
public record TicketSummary(
        Long id,
        String title,
        String categoryName,
        TicketPriority priority,
        TicketStatus status,
        @Nullable String assigneeName,
        Instant createdAt,
        Instant lastUpdatedAt) {}
