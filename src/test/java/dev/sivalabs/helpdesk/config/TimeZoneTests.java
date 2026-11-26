package dev.sivalabs.helpdesk.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sivalabs.helpdesk.BaseIT;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

/** The configured time zone applies to the application and to its database sessions. */
class TimeZoneTests extends BaseIT {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseTheConfiguredZoneInTheApplicationAndTheDatabase() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(jdbcTemplate.queryForObject("select current_setting('TimeZone')", String.class))
                .isEqualTo("Asia/Kolkata");
    }

    @Test
    void shouldApplyAnOverriddenZone() {
        var previous = TimeZone.getDefault();
        try {
            var environment = new MockEnvironment().withProperty(TimeZoneEnvironmentPostProcessor.PROPERTY, "UTC");
            new TimeZoneEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("UTC"));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
