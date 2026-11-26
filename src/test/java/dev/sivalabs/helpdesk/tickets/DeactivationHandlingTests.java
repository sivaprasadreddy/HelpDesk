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
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-022: tickets whose assignee or category is deactivated. */
@Sql("/test-data.sql")
class DeactivationHandlingTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);

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
                insert into users(id, email, password, name, role) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER'),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF'),
                (905, 'kiran@example.com', 'x', 'Kiran Patel', 'ROLE_STAFF')
                """);
        // Ravi: Electrical + Plumbing; Anita and Kiran: Electrical; nobody else handles Plumbing.
        sql("""
                insert into staff_categories(user_id, category_id) values
                (901, 2), (901, 1), (903, 2), (905, 2)
                """);
    }

    void ticket(long id, String status, long categoryId, Long assignedTo) {
        sql(
                "insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)"
                        + " values (?, ?, 'd', 'HIGH', ?, ?, 900, ?)",
                id,
                "Ticket " + id,
                status,
                categoryId,
                assignedTo);
    }

    Long assignee(long id) {
        return jdbcTemplate.queryForObject("select assigned_to from tickets where id = ?", Long.class, id);
    }

    void deactivate(long staffId) {
        assertThat(mvc.post()
                        .uri("/admin/staff/{id}/deactivate", staffId)
                        .with(user(ADMIN))
                        .with(csrf()))
                .hasStatus3xxRedirection()
                .flash()
                .containsEntry("successMessage", "Staff member deactivated; their open tickets were reassigned");
    }

    @Test
    void openWorkShouldBeSpreadOverRemainingHandlers() {
        ticket(1, "OPEN", 2, 901L);
        ticket(2, "IN_PROGRESS", 2, 901L);
        ticket(3, "OPEN", 2, 903L); // Anita already has one

        deactivate(901);

        // Ticket 1 goes to Kiran (no work), ticket 2 then ties Anita and Kiran at one each: lowest id wins.
        assertThat(assignee(1)).isEqualTo(905L);
        assertThat(assignee(2)).isEqualTo(903L);
        assertThat(assignee(3)).isEqualTo(903L);
    }

    @Test
    void openWorkWithNoOtherHandlerShouldBeUnassigned() {
        ticket(1, "OPEN", 1, 901L);

        deactivate(901);

        assertThat(assignee(1)).isNull();
        assertThat(mvc.get().uri("/tickets?assignee=none").with(user(ADMIN)))
                .bodyText()
                .contains("Ticket 1");
    }

    @Test
    void finishedWorkShouldKeepItsAssignee() {
        ticket(1, "RESOLVED", 2, 901L);
        ticket(2, "CLOSED", 2, 901L);

        deactivate(901);

        assertThat(assignee(1)).isEqualTo(901L);
        assertThat(assignee(2)).isEqualTo(901L);
    }

    @Test
    void deactivatingAnInactiveStaffMemberAgainShouldChangeNothing() {
        sql("update users set active = false where id = 903");
        ticket(1, "OPEN", 2, 903L); // e.g. assigned by hand before

        mvc.post()
                .uri("/admin/staff/{id}/deactivate", 903)
                .with(user(ADMIN))
                .with(csrf())
                .exchange();

        assertThat(assignee(1)).isEqualTo(903L);
    }

    @Test
    void ticketsShouldKeepADeactivatedCategoryLabelledInactive() {
        ticket(1, "OPEN", 2, 901L);

        mvc.post()
                .uri("/admin/categories/{id}/deactivate", 2)
                .with(user(ADMIN))
                .with(csrf())
                .exchange();

        assertThat(jdbcTemplate.queryForObject("select category_id from tickets where id = 1", Long.class))
                .isEqualTo(2L);
        assertThat(assignee(1)).isEqualTo(901L);
        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN))).bodyText().contains("Electrical (inactive)");
        assertThat(mvc.get().uri("/tickets").with(user(ADMIN))).bodyText().contains("Electrical (inactive)");
    }
}
