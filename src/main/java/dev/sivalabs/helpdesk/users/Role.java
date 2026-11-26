package dev.sivalabs.helpdesk.users;

public enum Role {
    ROLE_USER,
    ROLE_STAFF,
    ROLE_ADMIN;

    /**
     * ADMIN can do everything STAFF and USER can. STAFF and USER are siblings:
     * neither includes the other (STAFF cannot create tickets, USER cannot update status).
     */
    public static String getRoleHierarchy() {
        return """
                %s > %s
                %s > %s
                """.formatted(ROLE_ADMIN.name(), ROLE_STAFF.name(), ROLE_ADMIN.name(), ROLE_USER.name());
    }
}
