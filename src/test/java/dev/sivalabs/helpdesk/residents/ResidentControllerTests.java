package dev.sivalabs.helpdesk.residents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.sivalabs.helpdesk.BaseIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;

@Sql("/test-data.sql")
class ResidentControllerTests extends BaseIT {
    @Autowired
    JdbcTemplate jdbcTemplate;

    Map<String, Object> userByEmail(String email) {
        return jdbcTemplate.queryForMap("select id, email, role from users where email = ?", email);
    }

    int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/residents", "/admin/staff", "/admin/residents/new", "/admin/staff/new"})
    @WithMockUser(roles = "ADMIN")
    void shouldRenderAdminPages(String uri) {
        assertThat(mvc.get().uri(uri)).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "STAFF"})
    void shouldForbidNonAdmins(String role) {
        var result = mvc.get()
                .uri("/admin/residents")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                                "u")
                        .roles(role));
        assertThat(result).hasStatus(403);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldShowAdminNavigation() {
        assertThat(mvc.get().uri("/home"))
                .bodyText()
                .contains("/admin/residents")
                .contains("/admin/staff");
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldHideAdminNavigationForNonAdmin() {
        assertThat(mvc.get().uri("/home")).bodyText().doesNotContain("/admin/residents");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateResidentAndListIt() {
        var result = mvc.post()
                .uri("/admin/residents")
                .with(csrf())
                .param("name", "Jane Resident")
                .param("email", "Jane@Example.com")
                .param("password", "secret123")
                .param("flatNumber", " A-101 ")
                .param("mobile", "9876543210")
                .param("permanentAddress", "21 Marine Drive, Kochi");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/admin/residents");
        var user = userByEmail("jane@example.com");
        assertThat(user).containsEntry("role", "ROLE_USER");
        var id = user.get("id");
        assertThat(jdbcTemplate.queryForMap(
                        "select flat_number, mobile, permanent_address from resident_profiles where user_id = ?", id))
                .containsEntry("flat_number", "A-101")
                .containsEntry("mobile", "9876543210")
                .containsEntry("permanent_address", "21 Marine Drive, Kochi");
        assertThat(count("select count(*) from staff_profiles where user_id = ?", id))
                .isZero();
        assertThat(mvc.get().uri("/admin/residents"))
                .bodyText()
                .contains("Jane Resident")
                .contains("A-101")
                .contains("9876543210");
        assertThat(mvc.get().uri("/admin/staff")).bodyText().doesNotContain("Jane Resident");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRequireFlatNumberAndValidMobileForResidents() {
        var result = mvc.post()
                .uri("/admin/residents")
                .with(csrf())
                .param("name", "Jane")
                .param("email", "jane@example.com")
                .param("password", "secret123")
                .param("flatNumber", " ")
                .param("mobile", "12ab");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Flat number is required").contains("Enter a valid mobile number");
        assertThat(count("select count(*) from users where email = ?", "jane@example.com"))
                .isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldShowValidationErrors() {
        var result = mvc.post()
                .uri("/admin/residents")
                .with(csrf())
                .param("name", "")
                .param("email", "not-an-email")
                .param("password", "short");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Name is required")
                .contains("Enter a valid email address")
                .contains("Password must be at least 8 characters");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectDuplicateEmail() {
        var result = mvc.post()
                .uri("/admin/residents")
                .with(csrf())
                .param("name", "Another Admin")
                .param("email", "ADMIN@gmail.com")
                .param("password", "secret123")
                .param("flatNumber", "A-102")
                .param("mobile", "9876543210");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("A user with this email already exists");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404ForUnknownUserType() {
        assertThat(mvc.get().uri("/admin/aliens")).hasStatus(404);
    }
}
