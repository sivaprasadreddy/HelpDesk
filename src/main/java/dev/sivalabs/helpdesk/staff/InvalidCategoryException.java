package dev.sivalabs.helpdesk.staff;

class InvalidCategoryException extends RuntimeException {
    InvalidCategoryException() {
        super("One or more selected categories are not available");
    }
}
