package dev.sivalabs.helpdesk.users;

record CreateUserCmd(String name, String email, String password, Role role) {}
