package dev.sivalabs.helpdesk.tickets;

/** The chosen assignee is not an active staff member. */
class InvalidAssigneeException extends RuntimeException {
    InvalidAssigneeException(Long staffId) {
        super("User #" + staffId + " is not an active staff member");
    }
}
