package dev.sivalabs.helpdesk.tickets;

/** The ticket cannot move to the requested status from the one it is in (now). */
class InvalidStatusTransitionException extends RuntimeException {
    InvalidStatusTransitionException(String message) {
        super(message);
    }
}
