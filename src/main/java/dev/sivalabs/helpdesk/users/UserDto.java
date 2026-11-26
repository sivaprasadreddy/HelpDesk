package dev.sivalabs.helpdesk.users;

import java.time.Instant;
import java.util.Set;

/** {@code flatNumber}, {@code mobile} and {@code permanentAddress} come from the role's profile and may be null. */
public record UserDto(
        Long id,
        String name,
        String email,
        Role role,
        boolean active,
        Set<Long> categoryIds,
        Instant createdAt,
        String flatNumber,
        String mobile,
        String permanentAddress) {}
