package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
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

/** A staff member sees the tickets assigned to them. */
@Sql("/test-data.sql")
class AssignedTicketsTests extends BaseIT {
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser ANITA = new SecurityUser(903L, "Anita Sharma", "anita@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    /** The pool does not auto-commit, so fixture writes need a transaction. */
    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    @BeforeEach
    void setUp() {
        sql("""
                insert into users(id, email, password, name, role) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER'),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
    }

    void ticket(long id, String title, String status, Long assignedTo, int daysAgo) {
        sql(
                "insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to,"
                        + " created_at) values (?, ?, 'd', 'HIGH', ?, 2, 900, ?, CURRENT_TIMESTAMP - make_interval(days"
                        + " => ?))",
                id,
                title,
                status,
                assignedTo,
                daysAgo);
    }

    @Test
    void shouldListOnlyTicketsAssignedToTheStaffMember() {
        ticket(1, "Power outage in B block", "IN_PROGRESS", 901L, 2);
        ticket(2, "Corridor lights flickering", "OPEN", 901L, 1);
        ticket(3, "Lift stuck on floor four", "OPEN", 903L, 1);
        ticket(4, "Nobody handles this yet", "OPEN", null, 1);

        var result = mvc.get().uri("/tickets/assigned").with(user(RAVI));

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Assigned to Me")
                .contains("Power outage in B block")
                .contains("Corridor lights flickering")
                .doesNotContain("Lift stuck on floor four")
                .doesNotContain("Nobody handles this yet")
                .contains("Raised by")
                .contains("Priya Nair")
                .contains("Electrical")
                .doesNotContain("/tickets/new");
    }

    @Test
    void shouldFilterByStatusAndKeepNewestFirst() {
        ticket(1, "Older open ticket", "OPEN", 901L, 4);
        ticket(2, "Newer open ticket", "OPEN", 901L, 1);
        ticket(3, "Finished ticket", "CLOSED", 901L, 2);

        assertThat(mvc.get().uri("/tickets/assigned?status=OPEN").with(user(RAVI)))
                .hasStatusOk()
                .bodyText()
                .containsSubsequence("Newer open ticket", "Older open ticket")
                .doesNotContain("Finished ticket");
    }

    @Test
    void shouldShowEmptyStateWithoutCreateButton() {
        ticket(1, "Work for Ravi", "OPEN", 901L, 1);

        assertThat(mvc.get().uri("/tickets/assigned").with(user(ANITA)))
                .hasStatusOk()
                .bodyText()
                .contains("No tickets assigned to you")
                .doesNotContain("/tickets/new");
    }

    @Test
    void shouldAllowOnlyStaff() {
        assertThat(mvc.get().uri("/tickets/assigned").with(user(PRIYA))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets/assigned").with(user(ADMIN))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets/assigned"))
                .hasStatus3xxRedirection()
                .redirectedUrl()
                .endsWith("/login");
    }

    @Test
    void shouldShowAssignedLinkOnlyToStaff() {
        assertThat(mvc.get().uri("/home").with(user(RAVI)))
                .bodyText()
                .contains("/tickets/assigned")
                .doesNotContain("/tickets/mine")
                .doesNotContain("/tickets/new");
        assertThat(mvc.get().uri("/home").with(user(PRIYA))).bodyText().doesNotContain("/tickets/assigned");
        assertThat(mvc.get().uri("/home").with(user(ADMIN))).bodyText().doesNotContain("/tickets/assigned");
    }
}
