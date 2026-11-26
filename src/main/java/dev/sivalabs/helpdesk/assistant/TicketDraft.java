package dev.sivalabs.helpdesk.assistant;

import dev.sivalabs.helpdesk.tickets.TicketPriority;
import java.io.Serializable;

/**
 * A ticket the assistant proposed. Nothing is created until the user confirms it with the button under the draft,
 * so a model that skips asking cannot raise tickets on its own.
 */
public record TicketDraft(
        String id, String title, String description, TicketPriority priority, Long categoryId, String categoryName)
        implements Serializable {}
