package dev.sivalabs.helpdesk.tickets;

/** The images picked for a ticket break a rule: too many, too large, or not a supported image. */
class InvalidAttachmentException extends RuntimeException {
    InvalidAttachmentException(String message) {
        super(message);
    }
}
