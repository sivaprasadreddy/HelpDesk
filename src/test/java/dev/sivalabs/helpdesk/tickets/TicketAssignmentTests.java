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

/** REQ-013: new tickets go to the least-loaded active staff member who handles the category. */
@Sql("/test-data.sql")
class TicketAssignmentTests extends BaseIT {
    static final long RESIDENT_ID = 900L;
    static final SecurityUser RESIDENT =
            new SecurityUser(RESIDENT_ID, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);
    static final long PLUMBING = 1L;
    static final long ELECTRICAL = 2L;

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
        sql(
                "insert into users(id, email, password, name, role) values (?, 'priya@example.com', 'x', 'Priya Nair',"
                        + " 'ROLE_USER')",
                RESIDENT_ID);
    }

    void staff(long id, boolean active, long... categoryIds) {
        sql(
                "insert into users(id, email, password, name, role, active) values (?, ?, 'x', ?, 'ROLE_STAFF', ?)",
                id,
                "staff" + id + "@example.com",
                "Staff " + id,
                active);
        for (long categoryId : categoryIds) {
            sql("insert into staff_categories(user_id, category_id) values (?, ?)", id, categoryId);
        }
    }

    void existingTicket(long assignedTo, String status) {
        sql(
                "insert into tickets(title, description, priority, status, category_id, created_by, assigned_to)"
                        + " values ('t', 'd', 'LOW', ?, ?, ?, ?)",
                status,
                PLUMBING,
                RESIDENT_ID,
                assignedTo);
    }

    /** Creates a ticket as the resident and returns its assignee. */
    Long createTicket(long categoryId) {
        var title = "New ticket " + System.nanoTime();
        assertThat(mvc.post()
                        .uri("/tickets")
                        .with(user(RESIDENT))
                        .with(csrf())
                        .param("title", title)
                        .param("description", "Please fix.")
                        .param("priority", "MEDIUM")
                        .param("categoryId", String.valueOf(categoryId)))
                .hasStatus3xxRedirection();
        return jdbcTemplate.queryForObject("select assigned_to from tickets where title = ?", Long.class, title);
    }

    @Test
    void shouldAssignToStaffHandlingTheCategory() {
        staff(910, true, ELECTRICAL);
        staff(911, true, PLUMBING, ELECTRICAL);

        assertThat(createTicket(PLUMBING)).isEqualTo(911L);
    }

    @Test
    void shouldSkipDeactivatedStaff() {
        staff(910, false, PLUMBING);
        staff(911, true, PLUMBING);

        assertThat(createTicket(PLUMBING)).isEqualTo(911L);
    }

    @Test
    void shouldPickStaffWithFewestOpenOrInProgressTickets() {
        staff(910, true, PLUMBING);
        staff(911, true, PLUMBING);
        existingTicket(910, "OPEN");
        existingTicket(910, "IN_PROGRESS");
        existingTicket(911, "OPEN");
        // Finished work does not count towards the workload
        existingTicket(911, "RESOLVED");
        existingTicket(911, "CLOSED");

        assertThat(createTicket(PLUMBING)).isEqualTo(911L);
    }

    @Test
    void shouldSpreadTicketsAndBreakTiesByLowestId() {
        staff(911, true, PLUMBING);
        staff(910, true, PLUMBING);

        assertThat(createTicket(PLUMBING)).isEqualTo(910L);
        assertThat(createTicket(PLUMBING)).isEqualTo(911L);
        assertThat(createTicket(PLUMBING)).isEqualTo(910L);
    }

    @Test
    void shouldLeaveTicketUnassignedWhenNobodyActiveHandlesTheCategory() {
        staff(910, true, ELECTRICAL);
        staff(911, false, PLUMBING);

        assertThat(createTicket(PLUMBING)).isNull();
    }
}
