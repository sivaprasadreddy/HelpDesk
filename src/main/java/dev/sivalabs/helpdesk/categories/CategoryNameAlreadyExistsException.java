package dev.sivalabs.helpdesk.categories;

public class CategoryNameAlreadyExistsException extends RuntimeException {
    CategoryNameAlreadyExistsException(String name) {
        super("A category named " + name + " already exists");
    }
}
