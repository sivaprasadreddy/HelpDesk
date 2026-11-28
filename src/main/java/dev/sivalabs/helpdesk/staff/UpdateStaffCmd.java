package dev.sivalabs.helpdesk.staff;

import java.util.Set;

record UpdateStaffCmd(
        Long id, String name, String email, String mobile, String permanentAddress, Set<Long> categoryIds) {}
