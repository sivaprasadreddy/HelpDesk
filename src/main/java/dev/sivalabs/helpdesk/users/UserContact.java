package dev.sivalabs.helpdesk.users;

/** Who to write to: a user's name and email address. */
public record UserContact(Long id, String name, String email) {}
