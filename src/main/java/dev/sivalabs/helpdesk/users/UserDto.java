package dev.sivalabs.helpdesk.users;

import java.time.Instant;
import java.util.Set;

public record UserDto(
        Long id, String name, String email, Role role, boolean active, Set<Long> categoryIds, Instant createdAt) {}
