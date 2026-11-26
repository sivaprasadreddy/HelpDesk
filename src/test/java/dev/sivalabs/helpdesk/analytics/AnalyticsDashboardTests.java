package dev.sivalabs.helpdesk.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import dev.sivalabs.helpdesk.BaseIT;
import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/** The analytics dashboard (ADMIN only). */
@Sql("/test-data.sql")
class AnalyticsDashboardTests extends BaseIT {
    static final SecurityUser ADMIN = new SecurityUser(1L, "Administrator", "admin@gmail.com", "x", Role.ROLE_ADMIN);
    static final SecurityUser RAVI = new SecurityUser(901L, "Ravi Kumar", "ravi@example.com", "x", Role.ROLE_STAFF);
    static final SecurityUser PRIYA = new SecurityUser(900L, "Priya Nair", "priya@example.com", "x", Role.ROLE_USER);

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
                insert into tickets(title, description, priority, status, category_id, created_by, assigned_to,
                                    created_at, resolved_at) values
                ('a', 'd', 'HIGH', 'OPEN', 2, 900, 901, CURRENT_TIMESTAMP, null),
                ('b', 'd', 'LOW', 'RESOLVED', 1, 900, 901, CURRENT_TIMESTAMP - interval '3 hours', CURRENT_TIMESTAMP)
                """);
    }

    @Test
    void shouldRenderKpisChartsAndStaffTable() {
        assertThat(mvc.get().uri("/admin/analytics").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("Analytics")
                .contains("Last 30 days")
                .contains("Open or in progress")
                .contains("Created vs. resolved")
                .contains("By status")
                .contains("By priority")
                .contains("By category")
                .contains("Resolution time by priority")
                .contains("Staff workload and performance")
                .contains("Ravi Kumar")
                .contains("3h 0m")
                .contains("sl-chart");
    }

    @Test
    void shouldAcceptKnownPeriodsAndFallBackOtherwise() {
        assertThat(mvc.get().uri("/admin/analytics?days=7").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("last 7 days");
        assertThat(mvc.get().uri("/admin/analytics?days=1000").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("last 30 days");
    }

    @Test
    void shouldRenderWithNoTickets() {
        sql("delete from tickets");
        assertThat(mvc.get().uri("/admin/analytics").with(user(ADMIN)))
                .hasStatusOk()
                .bodyText()
                .contains("—");
    }

    @Test
    void onlyAdminMayViewDashboard() {
        assertThat(mvc.get().uri("/admin/analytics").with(user(RAVI))).hasStatus(403);
        assertThat(mvc.get().uri("/admin/analytics").with(user(PRIYA))).hasStatus(403);
        assertThat(mvc.get().uri("/home").with(user(ADMIN))).bodyText().contains("/admin/analytics");
        assertThat(mvc.get().uri("/home").with(user(RAVI))).bodyText().doesNotContain("/admin/analytics");
    }

    @Test
    void shouldHumanizeDurations() {
        assertThat(AnalyticsController.humanize(null)).isEqualTo("—");
        assertThat(AnalyticsController.humanize(Duration.ofSeconds(20))).isEqualTo("1m");
        assertThat(AnalyticsController.humanize(Duration.ofMinutes(45))).isEqualTo("45m");
        assertThat(AnalyticsController.humanize(Duration.ofMinutes(195))).isEqualTo("3h 15m");
        assertThat(AnalyticsController.humanize(Duration.ofHours(52))).isEqualTo("2d 4h");
    }
}
