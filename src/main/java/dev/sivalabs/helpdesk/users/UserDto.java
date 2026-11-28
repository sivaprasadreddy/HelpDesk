package dev.sivalabs.helpdesk.users;

import java.time.Instant;

/** An account, without role-specific profile details. */
public record UserDto(Long id, String name, String email, Role role, boolean active, Instant createdAt) {}
