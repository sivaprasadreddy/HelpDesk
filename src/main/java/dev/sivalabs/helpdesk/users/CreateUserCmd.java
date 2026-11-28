package dev.sivalabs.helpdesk.users;

/** A new account; the email address is normalised and must not be in use. */
public record CreateUserCmd(String name, String email, String password, Role role) {}
