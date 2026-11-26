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

/** The administrator sees every ticket and filters by status, priority, category and assignee. */
@Sql("/test-data.sql")
class AllTicketsTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);

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
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
    }

    void ticket(long id, String title, String status, String priority, long categoryId, Long assignedTo) {
        sql(
                "insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)"
                        + " values (?, ?, 'd', ?, ?, ?, 900, ?)",
                id,
                title,
                priority,
                status,
                categoryId,
                assignedTo);
    }

    void givenTickets() {
        ticket(1, "Power outage in B block", "IN_PROGRESS", "HIGH", 2, 901L);
        ticket(2, "Lift stuck on floor four", "OPEN", "CRITICAL", 6, 903L);
        ticket(3, "Leaking tap", "CLOSED", "LOW", 1, 903L);
        ticket(4, "Nobody handles this yet", "OPEN", "MEDIUM", 1, null);
    }

    @Test
    void shouldListEveryTicket() {
        givenTickets();

        assertThat(mvc.get().uri("/tickets").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("All Tickets")
                .contains("Power outage in B block")
                .contains("Lift stuck on floor four")
                .contains("Leaking tap")
                .contains("Nobody handles this yet")
                .contains("Raised by")
                .contains("Assigned to")
                .contains("Not yet assigned");
    }

    @Test
    void shouldFilterByStatus() {
        givenTickets();
        assertThat(mvc.get().uri("/tickets?status=OPEN").with(user(ADMIN)))
                .bodyText()
                .contains("Lift stuck on floor four", "Nobody handles this yet")
                .doesNotContain("Power outage in B block", "Leaking tap");
    }

    @Test
    void shouldFilterByPriorityAndCategory() {
        givenTickets();
        assertThat(mvc.get().uri("/tickets?priority=CRITICAL").with(user(ADMIN)))
                .bodyText()
                .contains("Lift stuck on floor four")
                .doesNotContain("Power outage in B block", "Leaking tap", "Nobody handles this yet");
        assertThat(mvc.get().uri("/tickets?category=1").with(user(ADMIN)))
                .bodyText()
                .contains("Leaking tap", "Nobody handles this yet")
                .doesNotContain("Power outage in B block", "Lift stuck on floor four");
    }

    @Test
    void shouldFilterByAssigneeOrUnassigned() {
        givenTickets();
        assertThat(mvc.get().uri("/tickets?assignee=903").with(user(ADMIN)))
                .bodyText()
                .contains("Lift stuck on floor four", "Leaking tap")
                .doesNotContain("Power outage in B block", "Nobody handles this yet");
        assertThat(mvc.get().uri("/tickets?assignee=none").with(user(ADMIN)))
                .bodyText()
                .contains("Nobody handles this yet")
                .doesNotContain("Power outage in B block", "Lift stuck on floor four", "Leaking tap");
    }

    @Test
    void shouldCombineFiltersAndShowNoMatchMessage() {
        givenTickets();
        assertThat(mvc.get().uri("/tickets?status=OPEN&assignee=903&category=6").with(user(ADMIN)))
                .bodyText()
                .contains("Lift stuck on floor four")
                .doesNotContain("Nobody handles this yet");
        assertThat(mvc.get().uri("/tickets?status=RESOLVED").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("No tickets match these filters.");
    }

    @Test
    void shouldIgnoreUnknownFilterValues() {
        givenTickets();
        assertThat(mvc.get()
                        .uri("/tickets?status=BOGUS&priority=x&category=abc&assignee=zz")
                        .with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("Power outage in B block", "Nobody handles this yet");
    }

    @Test
    void shouldShowEmptyStateWhenNoTickets() {
        assertThat(mvc.get().uri("/tickets").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("No tickets yet");
    }

    @Test
    void shouldAllowOnlyAdmin() {
        assertThat(mvc.get().uri("/tickets").with(user(RAVI))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets").with(user(PRIYA))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets"))
                .hasStatus3xxRedirection()
                .redirectedUrl()
                .endsWith("/login");
    }

    @Test
    void shouldShowAllTicketsLinkOnlyToAdmin() {
        assertThat(mvc.get().uri("/home").with(user(ADMIN))).bodyText().contains("All Tickets");
        assertThat(mvc.get().uri("/home").with(user(RAVI))).bodyText().doesNotContain("All Tickets");
        assertThat(mvc.get().uri("/home").with(user(PRIYA))).bodyText().doesNotContain("All Tickets");
    }
}
