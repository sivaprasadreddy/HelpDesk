package dev.sivalabs.helpdesk.users;

class EmailAlreadyExistsException extends RuntimeException {
    EmailAlreadyExistsException(String email) {
        super("A user with email " + email + " already exists");
    }
}
