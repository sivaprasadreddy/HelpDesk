package dev.sivalabs.helpdesk.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessPolicyTests {
    private final AccessPolicy policy = new AccessPolicy();

    private static final SecurityUser ADMIN = user(1L, Role.ROLE_ADMIN);
    private static final SecurityUser STAFF = user(2L, Role.ROLE_STAFF);
    private static final SecurityUser OTHER_STAFF = user(3L, Role.ROLE_STAFF);
    private static final SecurityUser RESIDENT = user(4L, Role.ROLE_USER);
    private static final SecurityUser OTHER_RESIDENT = user(5L, Role.ROLE_USER);

    // A ticket raised by RESIDENT (4) and assigned to STAFF (2)
    private static final Long CREATED_BY = 4L;
    private static final Long ASSIGNED_TO = 2L;

    @Test
    void createTickets() {
        assertThat(policy.canCreateTicket(ADMIN)).isTrue();
        assertThat(policy.canCreateTicket(RESIDENT)).isTrue();
        assertThat(policy.canCreateTicket(STAFF)).isFalse();
        assertThat(policy.canCreateTicket(null)).isFalse();
    }

    @Test
    void viewOwnTicketList() {
        assertThat(policy.canViewOwnTickets(RESIDENT)).isTrue();
        assertThat(policy.canViewOwnTickets(ADMIN)).isFalse();
        assertThat(policy.canViewOwnTickets(STAFF)).isFalse();
        assertThat(policy.canViewOwnTickets(null)).isFalse();
    }

    @Test
    void viewAssignedTicketList() {
        assertThat(policy.canViewAssignedTickets(STAFF)).isTrue();
        assertThat(policy.canViewAssignedTickets(ADMIN)).isFalse();
        assertThat(policy.canViewAssignedTickets(RESIDENT)).isFalse();
        assertThat(policy.canViewAssignedTickets(null)).isFalse();
    }

    @Test
    void viewTickets() {
        assertThat(policy.canViewTicket(ADMIN, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canViewTicket(STAFF, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canViewTicket(OTHER_STAFF, CREATED_BY, ASSIGNED_TO)).isFalse();
        assertThat(policy.canViewTicket(RESIDENT, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canViewTicket(OTHER_RESIDENT, CREATED_BY, ASSIGNED_TO))
                .isFalse();
        assertThat(policy.canViewTicket(STAFF, CREATED_BY, null)).isFalse();
    }

    @Test
    void ownRuleDoesNotLeakAcrossRoles() {
        // A staff member who happens to have created the ticket still only sees assigned tickets
        assertThat(policy.canViewTicket(STAFF, STAFF.getId(), null)).isFalse();
        // A resident is never "assigned"
        assertThat(policy.canUpdateTicketStatus(RESIDENT, RESIDENT.getId())).isFalse();
    }

    @Test
    void assignAndUpdateTickets() {
        assertThat(policy.canAssignTicket(ADMIN)).isTrue();
        assertThat(policy.canAssignTicket(STAFF)).isFalse();
        assertThat(policy.canAssignTicket(RESIDENT)).isFalse();

        assertThat(policy.canUpdateTicketStatus(ADMIN, null)).isTrue();
        assertThat(policy.canUpdateTicketStatus(STAFF, ASSIGNED_TO)).isTrue();
        assertThat(policy.canUpdateTicketStatus(OTHER_STAFF, ASSIGNED_TO)).isFalse();
        assertThat(policy.canUpdateTicketStatus(RESIDENT, ASSIGNED_TO)).isFalse();

        assertThat(policy.canUpdateTicketDetails(ADMIN)).isTrue();
        assertThat(policy.canUpdateTicketDetails(STAFF)).isFalse();
    }

    @Test
    void comments() {
        assertThat(policy.canComment(ADMIN, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canComment(STAFF, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canComment(OTHER_STAFF, CREATED_BY, ASSIGNED_TO)).isFalse();
        assertThat(policy.canComment(RESIDENT, CREATED_BY, ASSIGNED_TO)).isTrue();
        assertThat(policy.canComment(OTHER_RESIDENT, CREATED_BY, ASSIGNED_TO)).isFalse();
    }

    @Test
    void adminOnlyCapabilities() {
        for (var u : new SecurityUser[] {STAFF, RESIDENT}) {
            assertThat(policy.canManageUsers(u)).isFalse();
            assertThat(policy.canManageCategories(u)).isFalse();
            assertThat(policy.canViewAnalytics(u)).isFalse();
        }
        assertThat(policy.canManageUsers(ADMIN)).isTrue();
        assertThat(policy.canManageCategories(ADMIN)).isTrue();
        assertThat(policy.canViewAnalytics(ADMIN)).isTrue();
    }

    private static SecurityUser user(Long id, Role role) {
        return new SecurityUser(id, "u" + id, "u" + id + "@example.com", "x", role);
    }
}
