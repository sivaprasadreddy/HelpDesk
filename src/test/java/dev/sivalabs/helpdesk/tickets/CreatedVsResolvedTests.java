package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.sql.Timestamp;
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

/** Tickets created vs. resolved per day. */
@Sql("/test-data.sql")
class CreatedVsResolvedTests extends BaseIT {
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

    @BeforeEach
    void setUp() {
        sql(
                "insert into users(id, email, password, name, role) values (900, 'p@example.com', 'x', 'Priya', 'ROLE_USER')");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(ADMIN, null, ADMIN.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** A local time of day as the database stores it: UTC wall-clock time (Hibernate normalises instants). */
    static Timestamp at(LocalDate day, LocalTime time) {
        var instant = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant();
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    void ticket(Timestamp createdAt, @Nullable Timestamp resolvedAt) {
        sql(
                "insert into tickets(title, description, priority, status, category_id, created_by, created_at,"
                        + " resolved_at) values ('t', 'd', 'LOW', ?, 1, 900, ?, ?)",
                resolvedAt == null ? "OPEN" : "RESOLVED",
                createdAt,
                resolvedAt);
    }

    @Test
    void shouldCountPerDayFillingQuietDaysWithZeros() {
        ticket(at(D1, LocalTime.of(9, 0)), at(D1.plusDays(2), LocalTime.of(10, 0)));
        ticket(at(D1, LocalTime.of(23, 59)), null);
        ticket(at(D1.plusDays(2), LocalTime.of(0, 0)), at(D1.plusDays(2), LocalTime.of(18, 0)));
        ticket(at(D1.minusDays(1), LocalTime.of(12, 0)), at(D1.plusDays(3), LocalTime.of(0, 0))); // both outside

        var flow = analytics.getCreatedVsResolved(D1, D1.plusDays(2));

        assertThat(flow)
                .containsExactly(
                        new DailyTicketFlow(D1, 2, 0),
                        new DailyTicketFlow(D1.plusDays(1), 0, 0),
                        new DailyTicketFlow(D1.plusDays(2), 1, 2));
    }

    @Test
    void shouldAllowASingleDay() {
        assertThat(analytics.getCreatedVsResolved(D1, D1)).containsExactly(new DailyTicketFlow(D1, 0, 0));
    }

    @Test
    void shouldRejectInvalidPeriods() {
        assertThatThrownBy(() -> analytics.getCreatedVsResolved(D1, D1.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analytics.getCreatedVsResolved(D1, D1.plusDays(TicketAnalyticsAPI.MAX_DAYS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(analytics.getCreatedVsResolved(D1, D1.plusDays(TicketAnalyticsAPI.MAX_DAYS - 1)))
                .hasSize(TicketAnalyticsAPI.MAX_DAYS);
    }

    @Test
    void onlyAdminMayViewAnalytics() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(RAVI, null, RAVI.getAuthorities()));
        assertThatThrownBy(() -> analytics.getCreatedVsResolved(D1, D1)).isInstanceOf(AccessDeniedException.class);
    }
}
