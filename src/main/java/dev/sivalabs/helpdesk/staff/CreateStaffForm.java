package dev.sivalabs.helpdesk.staff;

import dev.sivalabs.helpdesk.users.ProfileFields;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateStaffForm(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Mobile is required")
        @Pattern(regexp = ProfileFields.MOBILE_PATTERN, message = ProfileFields.MOBILE_MESSAGE)
        String mobile,

        @Size(max = 500, message = "Permanent address must be at most 500 characters")
        String permanentAddress) {

    static CreateStaffForm empty() {
        return new CreateStaffForm("", "", "", "", "");
    }
}
