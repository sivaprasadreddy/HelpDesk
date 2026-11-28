package dev.sivalabs.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;
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
     * The MCP endpoint, for AI clients rather than browsers: HTTP Basic with the user's own email and password, no
     * session and no CSRF token. The tools check what each user may do, as the web pages do.
     */
    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) {
        http.securityMatcher("/mcp", "/mcp/**")
                .authorizeHttpRequests(r -> r.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * The live updates stream ({@code /live/**}) uses the signed-in session like the pages do, but answers 401 instead
     * of redirecting to the login page, and is never remembered as the page to return to after signing in.
     */
    @Bean
    @Order(2)
    SecurityFilterChain liveUpdatesSecurityFilterChain(HttpSecurity http) {
        http.securityMatcher("/live/**")
                .authorizeHttpRequests(r -> r.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .requestCache(c -> c.requestCache(new NullRequestCache()));
        return http.build();
    }

    /**
     * Coarse URL rules: whole areas that only ADMIN may enter. Per-ticket rules ("assigned", "own")
     * need the ticket, so they are checked with {@link dev.sivalabs.helpdesk.users.AccessPolicy}.
     */
    @Bean
    @Order(3)
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
