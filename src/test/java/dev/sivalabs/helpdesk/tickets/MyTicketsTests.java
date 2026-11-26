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

/** A resident sees the tickets they raised, and their status. */
@Sql("/test-data.sql")
class MyTicketsTests extends BaseIT {
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser ARJUN = new SecurityUser(902L, "Arjun Reddy", "arjun@example.com", "x", Role.ROLE_USER);
    static final SecurityUser STAFF = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
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
                (902, 'arjun@example.com', 'x', 'Arjun Reddy', 'ROLE_USER')
                """);
    }

    void ticket(long id, String title, String status, long createdBy, Long assignedTo, int daysAgo) {
        sql(
                "insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to,"
                        + " created_at) values (?, ?, 'd', 'HIGH', ?, 1, ?, ?, CURRENT_TIMESTAMP - make_interval(days"
                        + " => ?))",
                id,
                title,
                status,
                createdBy,
                assignedTo,
                daysAgo);
    }

    @Test
    void shouldListOnlyOwnTicketsWithStatusCategoryAndAssignee() {
        ticket(1, "Kitchen sink leaking", "IN_PROGRESS", 900, 901L, 2);
        ticket(2, "Bathroom tap dripping", "OPEN", 900, null, 1);
        ticket(3, "Broken window in D-12", "OPEN", 902, 901L, 1);

        var result = mvc.get().uri("/tickets/mine").with(user(PRIYA));

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Kitchen sink leaking")
                .contains("Bathroom tap dripping")
                .doesNotContain("Broken window in D-12")
                .contains("Plumbing")
                .contains("Ravi Kumar")
                .contains("Not yet assigned");
    }

    @Test
    void shouldShowNewestFirstByDefault() {
        ticket(1, "Older ticket", "OPEN", 900, null, 5);
        ticket(2, "Newer ticket", "OPEN", 900, null, 1);

        assertThat(mvc.get().uri("/tickets/mine").with(user(PRIYA)))
                .bodyText()
                .containsSubsequence("Newer ticket", "Older ticket");
    }

    @Test
    void shouldFilterByStatusAndCountPerStatus() {
        ticket(1, "Still open", "OPEN", 900, null, 3);
        ticket(2, "Being fixed", "IN_PROGRESS", 900, 901L, 2);
        ticket(3, "Already fixed", "RESOLVED", 900, 901L, 1);

        var result = mvc.get().uri("/tickets/mine?status=RESOLVED").with(user(PRIYA));

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Already fixed")
                .doesNotContain("Still open")
                .doesNotContain("Being fixed")
                .contains("aria-current=\"page\"");
        assertThat(mvc.get().uri("/tickets/mine?status=CLOSED").with(user(PRIYA)))
                .bodyText()
                .contains("No tickets with this status.");
    }

    @Test
    void shouldIgnoreUnknownStatusAndUnsortableColumns() {
        ticket(1, "Still open", "OPEN", 900, null, 1);

        assertThat(mvc.get()
                        .uri("/tickets/mine?status=BOGUS&sort=description,asc")
                        .with(user(PRIYA)))
                .hasStatusOk()
                .bodyText()
                .contains("Still open");
    }

    @Test
    void shouldSortByTitle() {
        ticket(1, "B ticket", "OPEN", 900, null, 2);
        ticket(2, "A ticket", "OPEN", 900, null, 1);
        ticket(3, "C ticket", "OPEN", 900, null, 3);

        assertThat(mvc.get().uri("/tickets/mine?sort=title,asc").with(user(PRIYA)))
                .bodyText()
                .containsSubsequence("A ticket", "B ticket", "C ticket");
    }

    @Test
    void shouldShowEmptyStateWhenResidentHasNoTickets() {
        ticket(1, "Ticket raised by Arjun", "OPEN", 902, null, 1);

        assertThat(mvc.get().uri("/tickets/mine").with(user(PRIYA)))
                .hasStatusOk()
                .bodyText()
                .contains("No tickets yet")
                .doesNotContain("Ticket raised by Arjun");
    }

    @Test
    void shouldAllowOnlyResidents() {
        assertThat(mvc.get().uri("/tickets/mine").with(user(STAFF))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets/mine").with(user(ADMIN))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets/mine"))
                .hasStatus3xxRedirection()
                .redirectedUrl()
                .endsWith("/login");
    }

    @Test
    void shouldShowMyTicketsLinkOnlyToResidents() {
        assertThat(mvc.get().uri("/home").with(user(PRIYA))).bodyText().contains("/tickets/mine");
        assertThat(mvc.get().uri("/home").with(user(ADMIN))).bodyText().doesNotContain("/tickets/mine");
        assertThat(mvc.get().uri("/home").with(user(STAFF))).bodyText().doesNotContain("/tickets/mine");
    }
}
