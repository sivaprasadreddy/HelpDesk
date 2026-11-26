package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-020: ADMIN edits any ticket's title, description, priority and category. */
@Sql("/test-data.sql")
class TicketEditTests extends BaseIT {
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
                insert into users(id, email, password, name, role) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER'),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF')
                """);
        sql("""
                insert into tickets(id, title, description, priority, status, category_id, created_by, assigned_to)
                values (1, 'Power outage', 'No power', 'LOW', 'IN_PROGRESS', 2, 900, 901)
                """);
    }

    MvcTestResult save(SecurityUser who, String title, String description, String priority, String categoryId) {
        return mvc.post()
                .uri("/tickets/1/edit")
                .with(user(who))
                .with(csrf())
                .param("title", title)
                .param("description", description)
                .param("priority", priority)
                .param("categoryId", categoryId)
                .exchange();
    }

    Map<String, Object> row() {
        return jdbcTemplate.queryForMap(
                "select title, description, priority, category_id, status, assigned_to from tickets where id = 1");
    }

    @Test
    void shouldRenderFormWithCurrentValues() {
        assertThat(mvc.get().uri("/tickets/1/edit").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("Edit Ticket #1")
                .contains("value=\"Power outage\"")
                .contains("No power")
                .contains("Save Changes");
    }

    @Test
    void shouldUpdateDetailsKeepingStatusAndAssignee() {
        assertThat(save(ADMIN, "  Power outage in B block ", " No power since 6am ", "CRITICAL", "6"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/tickets/1")
                .flash()
                .containsEntry("successMessage", "Ticket #1 updated successfully");

        var row = row();
        assertThat(row.get("title")).isEqualTo("Power outage in B block");
        assertThat(row.get("description")).isEqualTo("No power since 6am");
        assertThat(row.get("priority")).isEqualTo("CRITICAL");
        assertThat(((Number) row.get("category_id")).longValue()).isEqualTo(6L);
        assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Number) row.get("assigned_to")).longValue()).isEqualTo(901L);
    }

    @Test
    void shouldShowValidationErrors() {
        assertThat(save(ADMIN, "", "", "HIGH", "2"))
                .hasStatusOk()
                .bodyText()
                .contains("Title is required")
                .contains("Description is required");
        assertThat(row().get("title")).isEqualTo("Power outage");
    }

    @Test
    void shouldRejectMovingToAnInactiveCategory() {
        sql("update categories set active = false where id = 7");

        assertThat(save(ADMIN, "Power outage", "No power", "LOW", "7"))
                .hasStatusOk()
                .bodyText()
                .contains("Choose an active category");
    }

    @Test
    void shouldKeepTicketsCurrentCategoryEvenIfInactive() {
        sql("update categories set active = false where id = 2");

        assertThat(mvc.get().uri("/tickets/1/edit").with(user(ADMIN)))
                .bodyText()
                .contains("Electrical (inactive)");
        assertThat(save(ADMIN, "Renamed", "No power", "HIGH", "2")).hasStatus3xxRedirection();
        assertThat(row().get("title")).isEqualTo("Renamed");
    }

    @Test
    void onlyAdminMayEdit() {
        assertThat(mvc.get().uri("/tickets/1/edit").with(user(RAVI))).hasStatus(403);
        assertThat(mvc.get().uri("/tickets/1/edit").with(user(PRIYA))).hasStatus(403);
        assertThat(save(RAVI, "Hacked", "x", "LOW", "2")).hasStatus(403);
        assertThat(row().get("title")).isEqualTo("Power outage");

        assertThat(mvc.get().uri("/tickets/1").with(user(ADMIN))).bodyText().contains("/tickets/1/edit");
        assertThat(mvc.get().uri("/tickets/1").with(user(RAVI))).bodyText().doesNotContain("/tickets/1/edit");
    }

    @Test
    void shouldReturnNotFoundForMissingTicket() {
        assertThat(mvc.get().uri("/tickets/999/edit").with(user(ADMIN))).hasStatus(404);
    }
}
