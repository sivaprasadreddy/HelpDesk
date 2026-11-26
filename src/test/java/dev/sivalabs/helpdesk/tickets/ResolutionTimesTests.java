package dev.sivalabs.helpdesk.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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

/** REQ-025: average resolution time. */
@Sql("/test-data.sql")
class ResolutionTimesTests extends BaseIT {
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
        sql(
                "insert into users(id, email, password, name, role) values (900, 'p@example.com', 'x', 'Priya', 'ROLE_USER')");
        signInAs(ADMIN);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** A local time as the database stores it: UTC wall-clock time (Hibernate normalises instants). */
    static Timestamp at(LocalDate day, int hour) {
        var instant =
                day.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant();
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    void ticket(String priority, String status, Timestamp createdAt, @Nullable Timestamp resolvedAt) {
        sql(
                "insert into tickets(title, description, priority, status, category_id, created_by, created_at,"
                        + " resolved_at) values ('t', 'd', ?, ?, 1, 900, ?, ?)",
                priority,
                status,
                createdAt,
                resolvedAt);
    }

    @Test
    void shouldAverageOverallAndPerPriority() {
        ticket("HIGH", "RESOLVED", at(D1, 8), at(D1, 10)); // 2h
        ticket("HIGH", "CLOSED", at(D1, 8), at(D1, 14)); // 6h
        ticket("LOW", "RESOLVED", at(D1.minusDays(1), 9), at(D1.plusDays(1), 9)); // 48h
        ticket("LOW", "OPEN", at(D1, 8), null); // not resolved
        ticket("CRITICAL", "RESOLVED", at(D1, 8), at(D1.plusDays(5), 8)); // resolved outside the period

        var times = analytics.getResolutionTimes(D1, D1.plusDays(1));

        assertThat(times.resolvedCount()).isEqualTo(3);
        assertThat(times.average()).isEqualTo(Duration.ofHours(56).dividedBy(3));
        assertThat(times.byPriority())
                .extracting(
                        ResolutionTimes.ByPriority::priority,
                        ResolutionTimes.ByPriority::resolvedCount,
                        ResolutionTimes.ByPriority::average)
                .containsExactly(
                        tuple(TicketPriority.LOW, 1L, Duration.ofHours(48)),
                        tuple(TicketPriority.MEDIUM, 0L, null),
                        tuple(TicketPriority.HIGH, 2L, Duration.ofHours(4)),
                        tuple(TicketPriority.CRITICAL, 0L, null));
    }

    @Test
    void shouldHaveNoAverageWhenNothingWasResolved() {
        ticket("LOW", "OPEN", at(D1, 8), null);

        var times = analytics.getResolutionTimes(D1, D1);

        assertThat(times.resolvedCount()).isZero();
        assertThat(times.average()).isNull();
    }

    @Test
    void shouldRejectInvalidPeriod() {
        assertThatThrownBy(() -> analytics.getResolutionTimes(D1, D1.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAdminMayViewAnalytics() {
        signInAs(RAVI);
        assertThatThrownBy(() -> analytics.getResolutionTimes(D1, D1)).isInstanceOf(AccessDeniedException.class);
    }
}
