package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/** REQ-023: tickets by status, priority and category. */
@Sql("/test-data.sql")
class TicketBreakdownsTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);

    @Autowired
    TicketAnalyticsAPI analytics;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate tx;

    void sql(String statement, Object... args) {
        tx.executeWithoutResult(s -> jdbcTemplate.update(statement, args));
    }

    void signInAs(SecurityUser user) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    @BeforeEach
    void setUp() {
        sql(
                "insert into users(id, email, password, name, role) values (900, 'p@example.com', 'x', 'Priya', 'ROLE_USER')");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    void ticket(String status, String priority, long categoryId) {
        sql(
                "insert into tickets(title, description, priority, status, category_id, created_by)"
                        + " values ('t', 'd', ?, ?, ?, 900)",
                priority,
                status,
                categoryId);
    }

    @Test
    void shouldCountEveryGroupIncludingEmptyOnes() {
        ticket("OPEN", "HIGH", 2);
        ticket("OPEN", "LOW", 2);
        ticket("CLOSED", "HIGH", 1);
        signInAs(ADMIN);

        var b = analytics.getBreakdowns();

        assertThat(b.total()).isEqualTo(3);
        assertThat(b.byStatus())
                .extracting(CountByGroup::key, CountByGroup::label, CountByGroup::count)
                .containsExactly(
                        tuple("OPEN", "Open", 2L),
                        tuple("IN_PROGRESS", "In progress", 0L),
                        tuple("RESOLVED", "Resolved", 0L),
                        tuple("CLOSED", "Closed", 1L));
        assertThat(b.byPriority())
                .extracting(CountByGroup::key, CountByGroup::count)
                .containsExactly(tuple("LOW", 1L), tuple("MEDIUM", 0L), tuple("HIGH", 2L), tuple("CRITICAL", 0L));
        assertThat(b.byCategory())
                .extracting(CountByGroup::label, CountByGroup::count)
                .startsWith(tuple("Electrical", 2L), tuple("Plumbing", 1L))
                .contains(tuple("Gardening", 0L))
                .hasSize(7);
    }

    @Test
    void shouldListDeactivatedCategoriesOnlyWhenTheyHaveTickets() {
        ticket("OPEN", "HIGH", 7);
        sql("update categories set active = false where id in (6, 7)");
        signInAs(ADMIN);

        assertThat(analytics.getBreakdowns().byCategory())
                .extracting(CountByGroup::label)
                .contains("Gardening (inactive)")
                .doesNotContain("Lift Maintenance", "Lift Maintenance (inactive)");
    }

    @Test
    void shouldBeEmptyWithNoTickets() {
        signInAs(ADMIN);

        var b = analytics.getBreakdowns();

        assertThat(b.total()).isZero();
        assertThat(b.byStatus()).extracting(CountByGroup::count).containsOnly(0L);
    }

    @Test
    void onlyAdminMayViewAnalytics() {
        signInAs(RAVI);
        assertThatThrownBy(() -> analytics.getBreakdowns()).isInstanceOf(AccessDeniedException.class);
    }
}
