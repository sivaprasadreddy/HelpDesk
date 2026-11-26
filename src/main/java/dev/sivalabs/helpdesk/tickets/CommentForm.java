package dev.sivalabs.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CommentForm(
        @NotBlank(message = "Comment cannot be empty")
        @Size(max = 2000, message = "Comment must be at most 2000 characters")
        String content) {

    static CommentForm empty() {
        return new CommentForm("");
    }
}
