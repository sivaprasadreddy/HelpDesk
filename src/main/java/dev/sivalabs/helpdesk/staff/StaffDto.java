package dev.sivalabs.helpdesk.staff;

import java.time.Instant;
import java.util.Set;

/** A staff member with their profile; {@code mobile} and {@code permanentAddress} may be null. */
public record StaffDto(
        Long id,
        String name,
        String email,
        boolean active,
        Set<Long> categoryIds,
        Instant createdAt,
        String mobile,
        String permanentAddress) {}
