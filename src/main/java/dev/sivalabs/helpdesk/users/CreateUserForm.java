package dev.sivalabs.helpdesk.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateUserForm(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters")
        String password) {

    static CreateUserForm empty() {
        return new CreateUserForm("", "", "");
    }
}
