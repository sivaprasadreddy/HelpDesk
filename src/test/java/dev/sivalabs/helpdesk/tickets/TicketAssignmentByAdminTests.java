package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/** ADMIN assigns or reassigns a ticket to any active staff member. */
@Sql("/test-data.sql")
class TicketAssignmentByAdminTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    @BeforeEach
    void setUp() {
        sql("""
                insert into users(id, email, password, name, role, active) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER', true),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF', true),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF', true),
                (904, 'mohan@example.com', 'x', 'Mohan Das', 'ROLE_STAFF', false)
                """);
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage in B block', 'd', 'HIGH', 'OPEN', 2, 900, null)
                """);
    }

    MvcTestResult assign(SecurityUser who, Object staffId) {
        return mvc.post()
                .uri("/tickets/1/assignee")
                .with(user(who))
                .with(csrf())
                .param("staffId", String.valueOf(staffId))
                .exchange();
    }

    Long assignee() {
        return jdbcTemplate.queryForObject("select assigned_to from tickets where id = 1", Long.class);
    }

    @Test
    void adminShouldAssignAndReassign() {
        assertThat(assign(ADMIN, 901))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/tickets/1")
                .flash()
                .containsEntry("successMessage", "Ticket #1 assigned to Ravi Kumar");
        assertThat(assignee()).isEqualTo(901L);

        assign(ADMIN, 903);
        assertThat(assignee()).isEqualTo(903L);

        assertThat(assign(ADMIN, 903))
                .flash()
                .containsEntry("successMessage", "Ticket #1 is already assigned to Anita Sharma");
    }

    @Test
    void shouldRejectInactiveStaffAndNonStaff() {
        assertThat(assign(ADMIN, 904)).flash().containsEntry("errorMessage", "User #904 is not an active staff member");
        assertThat(assign(ADMIN, 900)).flash().containsEntry("errorMessage", "User #900 is not an active staff member");
        assertThat(assignee()).isNull();
    }

    @Test
    void closedTicketShouldNotBeReassigned() {
        sql("update tickets set status = 'CLOSED', assigned_to = 901 where id = 1");

        assertThat(assign(ADMIN, 903))
                .flash()
                .containsEntry("errorMessage", "Ticket #1 is closed and cannot be reassigned");
        assertThat(assignee()).isEqualTo(901L);
        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN))).bodyText().doesNotContain("/tickets/1/assignee");
    }

    @Test
    void onlyAdminMayAssign() {
        sql("update tickets set assigned_to = 901 where id = 1");

        assertThat(assign(RAVI, 903)).hasStatus(403);
        assertThat(assign(PRIYA, 903)).hasStatus(403);
        assertThat(assignee()).isEqualTo(901L);
    }

    @Test
    void shouldReturnNotFoundForMissingTicket() {
        assertThat(mvc.post()
                        .uri("/tickets/999/assignee")
                        .with(user(ADMIN))
                        .with(csrf())
                        .param("staffId", "901"))
                .hasStatus(404);
    }

    @Test
    void detailsPageShouldOfferActiveStaffToAdminOnly() {
        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN)))
                .bodyText()
                .contains("Assign ticket")
                .contains("Ravi Kumar")
                .contains("Anita Sharma")
                .doesNotContain("Mohan Das");

        sql("update tickets set assigned_to = 901 where id = 1");
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI))).bodyText().doesNotContain("/tickets/1/assignee");
    }
}
