package dev.sivalabs.helpdesk.users;

import java.util.Set;

record UpdateStaffCmd(
        Long id, String name, String email, String mobile, String permanentAddress, Set<Long> categoryIds) {}
