package dev.sivalabs.helpdesk.users;

/** Shared validation rules for the profile fields on the user forms. */
final class ProfileFields {
    static final String MOBILE_PATTERN = "^$|^\\+?[0-9]{10,15}$";
    static final String MOBILE_MESSAGE = "Enter a valid mobile number (10 to 15 digits, optional leading +)";

    private ProfileFields() {}

    /** Trimmed value, or null when blank. */
    static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
