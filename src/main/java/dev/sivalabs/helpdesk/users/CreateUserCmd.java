package dev.sivalabs.helpdesk.users;

/** {@code flatNumber} applies to residents only; {@code mobile} and {@code permanentAddress} to residents and staff. */
record CreateUserCmd(
        String name,
        String email,
        String password,
        Role role,
        String flatNumber,
        String mobile,
        String permanentAddress) {}
