package dev.sivalabs.helpdesk.config;

import dev.sivalabs.helpdesk.users.Role;
import dev.sivalabs.helpdesk.users.SecurityUser;
import io.github.wimdeblauwe.shadleaf.security.CurrentUserResolver;
import io.github.wimdeblauwe.shadleaf.security.DefaultCurrentUserResolver;
import io.github.wimdeblauwe.shadleaf.security.ShadleafUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(Role.getRoleHierarchy());
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder pe) {
        var authProvider = new DaoAuthenticationProvider(uds);
        authProvider.setPasswordEncoder(pe);

        return new ProviderManager(authProvider);
    }

    /**
     * Shows the user's display name (not the email they sign in with) in Shadleaf's user menu and #slUser.
     */
    @Bean
    CurrentUserResolver currentUserResolver() {
        CurrentUserResolver fallback = new DefaultCurrentUserResolver();
        return authentication -> authentication.getPrincipal() instanceof SecurityUser user
                ? ShadleafUser.of(user.getName(), user.getUsername(), user.getUsername(), null)
                : fallback.resolve(authentication);
    }
}
