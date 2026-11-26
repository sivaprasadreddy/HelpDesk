package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
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

/** REQ-026: workload and performance per staff member. */
@Sql("/test-data.sql")
class StaffPerformanceTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final LocalDate D1 = LocalDate.of(2026, 11, 1);

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
        sql("""
                insert into users(id, email, password, name, role, active) values
                (900, 'priya@example.com', 'x', 'Priya Nair', 'ROLE_USER', true),
                (901, 'ravi@example.com', 'x', 'Ravi Kumar', 'ROLE_STAFF', true),
                (903, 'anita@example.com', 'x', 'Anita Sharma', 'ROLE_STAFF', true),
                (904, 'mohan@example.com', 'x', 'Mohan Das', 'ROLE_STAFF', false),
                (905, 'kiran@example.com', 'x', 'Kiran Patel', 'ROLE_STAFF', false)
                """);
        signInAs(ADMIN);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    static Timestamp at(LocalDate day, int hour) {
        var instant =
                day.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant();
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    void ticket(
            String status, @Nullable Long assignedTo, @Nullable Timestamp createdAt, @Nullable Timestamp resolvedAt) {
        sql(
                "insert into tickets(title, description, priority, status, category_id, created_by, assigned_to,"
                        + " created_at, resolved_at) values ('t', 'd', 'LOW', ?, 1, 900, ?, coalesce(?,"
                        + " CURRENT_TIMESTAMP), ?)",
                status,
                assignedTo,
                createdAt,
                resolvedAt);
    }

    @Test
    void shouldReportWorkloadAndPerformanceBusiestFirst() {
        ticket("OPEN", 901L, null, null);
        ticket("IN_PROGRESS", 901L, null, null);
        ticket("RESOLVED", 901L, at(D1, 8), at(D1, 12)); // 4h, in period
        ticket("OPEN", 903L, null, null);
        ticket("CLOSED", 903L, at(D1, 8), at(D1, 10)); // 2h, in period
        ticket("CLOSED", 903L, at(D1, 8), at(D1, 16)); // 8h, in period
        ticket("CLOSED", 903L, at(D1.minusDays(9), 8), at(D1.minusDays(9), 9)); // outside period
        ticket("OPEN", null, null, null); // unassigned: nobody's workload

        var rows = analytics.getStaffPerformance(D1, D1);

        assertThat(rows)
                .containsExactly(
                        new StaffPerformance(901L, "Ravi Kumar", true, 1, 1, 1, Duration.ofHours(4)),
                        new StaffPerformance(903L, "Anita Sharma", true, 1, 0, 2, Duration.ofHours(5)));
        assertThat(rows.getFirst().workload()).isEqualTo(2);
    }

    @Test
    void shouldListIdleActiveStaffAndOnlyBusyInactiveStaff() {
        ticket("RESOLVED", 904L, at(D1, 8), at(D1, 9)); // inactive Mohan resolved one in the period

        assertThat(analytics.getStaffPerformance(D1, D1))
                .extracting(StaffPerformance::name)
                .containsExactly("Anita Sharma", "Mohan Das", "Ravi Kumar")
                .doesNotContain("Kiran Patel");
    }

    @Test
    void idleStaffShouldHaveNoAverage() {
        assertThat(analytics.getStaffPerformance(D1, D1)).allSatisfy(p -> {
            assertThat(p.workload()).isZero();
            assertThat(p.averageResolutionTime()).isNull();
        });
    }

    @Test
    void shouldRejectInvalidPeriod() {
        assertThatThrownBy(() -> analytics.getStaffPerformance(D1, D1.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAdminMayViewAnalytics() {
        signInAs(RAVI);
        assertThatThrownBy(() -> analytics.getStaffPerformance(D1, D1)).isInstanceOf(AccessDeniedException.class);
    }
}
