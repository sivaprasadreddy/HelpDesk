package dev.sivalabs.helpdesk.config;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Makes {@code helpdesk.timezone} the JVM's default time zone before any bean or database connection is created.
 * The application shows and groups dates in it ({@link ZoneId#systemDefault()}), and the PostgreSQL driver sets
 * each connection's session {@code TimeZone} from it, so {@code CURRENT_TIMESTAMP} and date functions in SQL agree.
 * Runs after the configuration files are loaded, so the property can come from any of them or the environment.
 */
public class TimeZoneEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    public static final String PROPERTY = "helpdesk.timezone";
    public static final String DEFAULT = "Asia/Kolkata";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var zone = ZoneId.of(environment.getProperty(PROPERTY, DEFAULT));
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
