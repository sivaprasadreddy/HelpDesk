package dev.sivalabs.helpdesk.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CategoryForm(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description) {

    static CategoryForm empty() {
        return new CategoryForm("", "");
    }

    static CategoryForm from(CategoryDto dto) {
        return new CategoryForm(dto.name(), dto.description() == null ? "" : dto.description());
    }
}
