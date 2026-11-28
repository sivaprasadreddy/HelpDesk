package dev.sivalabs.helpdesk.users;

public class EmailAlreadyExistsException extends RuntimeException {
    EmailAlreadyExistsException(String email) {
        super("A user with email " + email + " already exists");
    }
}
