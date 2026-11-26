package dev.sivalabs.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
class WebSecurityConfig {
    private static final String[] PUBLIC_RESOURCES = {
        "/",
        "/css/**",
        "/js/**",
        "/images/**",
        "/webjars/**",
        "/shadleaf/**",
        "/favicon.ico",
        "/actuator/health/**",
        "/actuator/info/**",
        "/error",
        "/login"
    };

    /**
     * Coarse URL rules: whole areas that only ADMIN may enter. Per-ticket rules ("assigned", "own")
     * need the ticket, so they are checked with {@link dev.sivalabs.helpdesk.users.AccessPolicy}.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(r -> r.requestMatchers(PUBLIC_RESOURCES)
                .permitAll()
                .requestMatchers("/admin/**", "/analytics/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());

        http.formLogin(formLogin ->
                formLogin.loginPage("/login").defaultSuccessUrl("/home").permitAll());

        http.logout(logout -> logout.logoutRequestMatcher(PathPatternRequestMatcher.pathPattern("/logout"))
                .logoutSuccessUrl("/")
                .permitAll());

        return http.build();
    }
}
