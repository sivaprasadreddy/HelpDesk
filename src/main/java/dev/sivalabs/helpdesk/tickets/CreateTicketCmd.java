package dev.sivalabs.helpdesk.tickets;

import java.util.List;

record CreateTicketCmd(
        String title,
        String description,
        TicketPriority priority,
        Long categoryId,
        Long createdBy,
        List<ImageUpload> images) {

    CreateTicketCmd {
        images = images == null ? List.of() : List.copyOf(images);
    }

    CreateTicketCmd(String title, String description, TicketPriority priority, Long categoryId, Long createdBy) {
        this(title, description, priority, categoryId, createdBy, List.of());
    }
}
