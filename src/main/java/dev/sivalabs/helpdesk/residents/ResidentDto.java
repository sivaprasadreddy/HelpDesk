package dev.sivalabs.helpdesk.residents;

import java.time.Instant;

/** A resident with their profile; the profile fields may be null for residents created without one. */
record ResidentDto(
        Long id,
        String name,
        String email,
        boolean active,
        Instant createdAt,
        String flatNumber,
        String mobile,
        String permanentAddress) {}
