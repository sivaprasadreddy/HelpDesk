package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/** The status workflow, for the assigned STAFF member and ADMIN. */
@Sql("/test-data.sql")
class TicketStatusWorkflowTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
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
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF')
                """);
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage in B block', 'd', 'HIGH', 'OPEN', 2, 900, 901)
                """);
    }

    MvcTestResult move(SecurityUser who, String from, String to) {
        return mvc.post()
                .uri("/tickets/1/status")
                .with(user(who))
                .with(csrf())
                .param("from", from)
                .param("to", to)
                .exchange();
    }

    String status() {
        return jdbcTemplate.queryForObject("select status from tickets where id = 1", String.class);
    }

    Timestamp column(String name) {
        return jdbcTemplate.queryForObject("select " + name + " from tickets where id = 1", Timestamp.class);
    }

    @Test
    void assignedStaffShouldMoveTicketThroughTheWorkflow() {
        assertThat(move(RAVI, "OPEN", "IN_PROGRESS"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/tickets/1")
                .flash()
                .containsEntry("successMessage", "Ticket #1 moved to In progress");
        assertThat(status()).isEqualTo("IN_PROGRESS");

        move(RAVI, "IN_PROGRESS", "RESOLVED");
        assertThat(status()).isEqualTo("RESOLVED");
        assertThat(column("resolved_at")).isNotNull();

        move(RAVI, "RESOLVED", "CLOSED");
        assertThat(status()).isEqualTo("CLOSED");
        assertThat(column("closed_at")).isNotNull();
    }

    @Test
    void reopeningShouldClearResolutionTime() {
        sql("update tickets set status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP where id = 1");

        move(ADMIN, "RESOLVED", "IN_PROGRESS");

        assertThat(status()).isEqualTo("IN_PROGRESS");
        assertThat(column("resolved_at")).isNull();
    }

    @Test
    void shouldRejectSkippingSteps() {
        assertThat(move(ADMIN, "OPEN", "CLOSED"))
                .hasStatus3xxRedirection()
                .flash()
                .containsEntry("errorMessage", "A ticket that is Open cannot be moved to Closed");
        assertThat(status()).isEqualTo("OPEN");
    }

    @Test
    void shouldRejectStaleStatus() {
        sql("update tickets set status = 'IN_PROGRESS' where id = 1");

        assertThat(move(ADMIN, "OPEN", "IN_PROGRESS"))
                .flash()
                .containsEntry("errorMessage", "Ticket #1 is now In progress; review it and try again");
    }

    @Test
    void closedTicketShouldBeFinal() {
        sql("update tickets set status = 'CLOSED' where id = 1");

        move(ADMIN, "CLOSED", "IN_PROGRESS");

        assertThat(status()).isEqualTo("CLOSED");
        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN))).bodyText().doesNotContain("/tickets/1/status");
    }

    @Test
    void onlyAdminAndAssignedStaffMayChangeStatus() {
        assertThat(move(PRIYA, "OPEN", "IN_PROGRESS")).hasStatus(403);
        assertThat(move(ANITA, "OPEN", "IN_PROGRESS")).hasStatus(404);
        assertThat(status()).isEqualTo("OPEN");

        move(ADMIN, "OPEN", "IN_PROGRESS");
        assertThat(status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void detailsPageShouldOfferNextStepsOnlyToThoseWhoMayChangeStatus() {
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI)))
                .bodyText()
                .contains("/tickets/1/status")
                .contains("Start work");
        assertThat(mvc.get().uri("/tickets/1").with(user(PRIYA))).bodyText().doesNotContain("/tickets/1/status");

        sql("update tickets set status = 'IN_PROGRESS' where id = 1");
        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN)))
                .bodyText()
                .contains("Mark resolved")
                .contains("Move back to Open");
    }
}
