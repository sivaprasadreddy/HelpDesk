package dev.sivalabs.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateTicketForm(
        @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Priority is required") TicketPriority priority,

        @NotNull(message = "Category is required") Long categoryId) {

    static CreateTicketForm empty() {
        return new CreateTicketForm("", "", TicketPriority.MEDIUM, null);
    }
}
