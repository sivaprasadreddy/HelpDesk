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

/** The ticket details page, visible to ADMIN, the assigned STAFF member and the resident who raised it. */
@Sql("/test-data.sql")
class TicketDetailsTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser ARJUN = new SecurityUser(902L, "Arjun Rao", "arjun@example.com", "x", Role.ROLE_USER);
    static final SecurityUser ANITA = new SecurityUser(903L, "Anita Sharma", "anita@example.com", "x", Role.ROLE_STAFF);

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
                (902, 'arjun@example.com', 'x', 'Arjun Rao', 'ROLE_USER'),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage in B block', 'No power since morning in flats B-101 to B-110.', 'CRITICAL',
                        'IN_PROGRESS', 2, 900, 901),
                       (2, 'Nobody handles this yet', 'Unassigned ticket', 'LOW', 'OPEN', 1, 900, null)
                """);
    }

    @Test
    void shouldShowTicketDetailsToTheResidentWhoRaisedIt() {
        assertThat(mvc.get().uri("/tickets/1").with(user(PRIYA)))
                .hasStatusOk()
                .bodyText()
                .contains("Ticket #1")
                .contains("Power outage in B block")
                .contains("No power since morning in flats B-101 to B-110.")
                .contains("Electrical")
                .contains("In progress")
                .contains("Critical")
                .contains("Priya Nair")
                .contains("Ravi Kumar")
                .contains("href=\"/tickets/mine\"");
    }

    @Test
    void shouldShowTicketToAssignedStaffAndAdmin() {
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI)))
                .hasStatusOk()
                .bodyText()
                .contains("Power outage in B block")
                .contains("href=\"/tickets/assigned\"");
        assertThat(mvc.get().uri("/tickets/2").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("Nobody handles this yet")
                .contains("Not yet assigned")
                .contains("All Tickets");
    }

    @Test
    void shouldHideTicketFromOtherResidentsAndStaff() {
        assertThat(mvc.get().uri("/tickets/1").with(user(ARJUN))).hasStatus(404);
        assertThat(mvc.get().uri("/tickets/1").with(user(ANITA))).hasStatus(404);
        assertThat(mvc.get().uri("/tickets/2").with(user(RAVI))).hasStatus(404);
    }

    @Test
    void shouldReturnNotFoundForMissingTicket() {
        assertThat(mvc.get().uri("/tickets/999").with(user(ADMIN))).hasStatus(404);
    }

    @Test
    void shouldRequireLogin() {
        assertThat(mvc.get().uri("/tickets/1"))
                .hasStatus3xxRedirection()
                .redirectedUrl()
                .endsWith("/login");
    }

    @Test
    void shouldLinkTicketTitlesInListsToDetails() {
        assertThat(mvc.get().uri("/tickets/mine").with(user(PRIYA))).bodyText().contains("href=\"/tickets/1\"");
    }
}
