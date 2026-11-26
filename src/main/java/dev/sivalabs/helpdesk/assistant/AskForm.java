package dev.sivalabs.helpdesk.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AskForm(
        @NotBlank(message = "Type a question") @Size(max = 2000, message = "Questions must be at most 2000 characters")
        String message) {

    static AskForm empty() {
        return new AskForm("");
    }
}
