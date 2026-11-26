package dev.sivalabs.helpdesk.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

record UpdateStaffForm(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
        String email,

        Set<Long> categoryIds) {

    UpdateStaffForm {
        categoryIds = categoryIds == null ? new LinkedHashSet<>() : categoryIds;
    }

    static UpdateStaffForm from(UserDto user) {
        return new UpdateStaffForm(user.name(), user.email(), new LinkedHashSet<>(user.categoryIds()));
    }
}
