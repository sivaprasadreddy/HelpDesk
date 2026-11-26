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
import org.springframework.transaction.support.TransactionTemplate;

@Sql("/test-data.sql")
class TicketControllerTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser RESIDENT = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final SecurityUser STAFF = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

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
        sql("insert into users(id, email, password, name, role) values (900, 'priya@example.com', 'x', 'Priya Nair',"
                + " 'ROLE_USER'), (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF')");
    }

    Map<String, Object> onlyTicket() {
        return jdbcTemplate.queryForMap("select * from tickets");
    }

    @Test
    void shouldRenderFormWithActiveCategoriesOnly() {
        sql("update categories set active = false where id = 7");

        var result = mvc.get().uri("/tickets/new").with(user(RESIDENT));

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("New Ticket")
                .contains("Plumbing")
                .contains("Critical")
                .doesNotContain("Gardening");
    }

    @Test
    void shouldCancelToTheRolesTicketList() {
        assertThat(mvc.get().uri("/tickets/new").with(user(RESIDENT)))
                .bodyText()
                .contains("href=\"/tickets/mine\"");
        assertThat(mvc.get().uri("/tickets/new").with(user(ADMIN))).bodyText().contains("href=\"/tickets\"");
    }

    @Test
    void shouldLetResidentCreateOpenUnassignedTicket() {
        var result = mvc.post()
                .uri("/tickets")
                .with(user(RESIDENT))
                .with(csrf())
                .param("title", "  Kitchen sink leaking ")
                .param("description", "Water drips under the sink in A-101.")
                .param("priority", "HIGH")
                .param("categoryId", "1")
                .exchange();

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/tickets/mine");
        assertThat(result).flash().containsKey("successMessage");
        var ticket = onlyTicket();
        assertThat(ticket)
                .containsEntry("title", "Kitchen sink leaking")
                .containsEntry("priority", "HIGH")
                .containsEntry("status", "OPEN")
                .containsEntry("category_id", 1L)
                .containsEntry("created_by", 900L)
                .containsEntry("assigned_to", null);
        assertThat(ticket.get("created_at")).isNotNull();
    }

    @Test
    void shouldLetAdminCreateTicket() {
        var result = mvc.post()
                .uri("/tickets")
                .with(user(ADMIN))
                .with(csrf())
                .param("title", "Lift stuck")
                .param("description", "B block lift is stuck on floor 4.")
                .param("priority", "CRITICAL")
                .param("categoryId", "6");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/tickets");
        assertThat(onlyTicket()).containsEntry("created_by", 1L).containsEntry("priority", "CRITICAL");
    }

    @Test
    void shouldRejectMissingFields() {
        var result = mvc.post()
                .uri("/tickets")
                .with(user(RESIDENT))
                .with(csrf())
                .param("title", " ")
                .param("description", "")
                .param("priority", "")
                .param("categoryId", "");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Title is required")
                .contains("Description is required")
                .contains("Priority is required")
                .contains("Category is required");
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isZero();
    }

    @Test
    void shouldRejectInactiveOrUnknownCategory() {
        sql("update categories set active = false where id = 7");

        for (var categoryId : new String[] {"7", "9999"}) {
            assertThat(mvc.post()
                            .uri("/tickets")
                            .with(user(RESIDENT))
                            .with(csrf())
                            .param("title", "Hedges")
                            .param("description", "Trim the hedges.")
                            .param("priority", "LOW")
                            .param("categoryId", categoryId))
                    .hasStatusOk()
                    .bodyText()
                    .contains("Choose an active category");
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isZero();
    }

    @Test
    void shouldForbidStaffFromCreatingTickets() {
        assertThat(mvc.get().uri("/tickets/new").with(user(STAFF))).hasStatus(403);
        assertThat(mvc.post()
                        .uri("/tickets")
                        .with(user(STAFF))
                        .with(csrf())
                        .param("title", "x")
                        .param("description", "x")
                        .param("priority", "LOW")
                        .param("categoryId", "1"))
                .hasStatus(403);
        assertThat(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class))
                .isZero();
    }

    @Test
    void shouldRedirectAnonymousToLogin() {
        assertThat(mvc.get().uri("/tickets/new"))
                .hasStatus3xxRedirection()
                .redirectedUrl()
                .endsWith("/login");
    }

    @Test
    void shouldShowNewTicketLinkOnlyToThoseWhoCanCreate() {
        assertThat(mvc.get().uri("/home").with(user(RESIDENT))).bodyText().contains("/tickets/new");
        assertThat(mvc.get().uri("/home").with(user(ADMIN))).bodyText().contains("/tickets/new");
        assertThat(mvc.get().uri("/home").with(user(STAFF))).bodyText().doesNotContain("/tickets/new");
    }
}
