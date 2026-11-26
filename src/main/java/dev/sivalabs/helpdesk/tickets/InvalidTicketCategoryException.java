package dev.sivalabs.helpdesk.tickets;

public class InvalidTicketCategoryException extends RuntimeException {
    InvalidTicketCategoryException(Long categoryId) {
        super("Category " + categoryId + " does not exist or is not active");
    }
}
