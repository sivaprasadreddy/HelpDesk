package dev.sivalabs.helpdesk.users;

class InvalidCategoryException extends RuntimeException {
    InvalidCategoryException() {
        super("One or more selected categories are not available");
    }
}
