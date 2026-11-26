package dev.sivalabs.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

record CreateTicketForm(
        @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Priority is required") TicketPriority priority,

        @NotNull(message = "Category is required") Long categoryId,

        List<MultipartFile> images) {

    CreateTicketForm {
        // A file input left empty still submits one empty part.
        images = images == null
                ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    static CreateTicketForm empty() {
        return new CreateTicketForm("", "", TicketPriority.MEDIUM, null, List.of());
    }
}
