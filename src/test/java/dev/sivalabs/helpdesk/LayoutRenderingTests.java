package dev.sivalabs.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.servlet.ViewResolver;

class LayoutRenderingTests extends BaseIT {
    @Autowired
    ViewResolver thymeleafViewResolver;

    @Test
    void shouldRenderLandingPageForAnonymousVisitor() {
        var result = mvc.get().uri("/");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Customer support, in one place").doesNotContain("sidebar");
    }

    @Test
    void shouldRequireLoginForHomePage() {
        var result = mvc.get().uri("/home");

        assertThat(result).hasStatus3xxRedirection().redirectedUrl().endsWith("/login");
    }

    @Test
    @WithMockUser(username = "siva@gmail.com")
    void shouldRenderHomePageWithMainLayout() {
        var result = mvc.get().uri("/home");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyText()
                .contains("Welcome back")
                .contains("siva@gmail.com")
                .contains("sidebar");
    }

    @Test
    @Sql("/test-data.sql")
    @WithUserDetails(
            value = "admin@gmail.com",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION)
    void shouldShowUserNameInsteadOfEmail() {
        var result = mvc.get().uri("/home");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().contains("Welcome back, Administrator.");
    }

    @Test
    @WithMockUser(username = "siva@gmail.com")
    void shouldRedirectSignedInUserFromLoginToHome() {
        var result = mvc.get().uri("/login");

        assertThat(result).hasStatus3xxRedirection().hasRedirectedUrl("/home");
    }

    @ParameterizedTest
    @ValueSource(strings = {"error/403", "error/404", "error/500"})
    void shouldRenderErrorPagesWithPublicLayout(String viewName) throws Exception {
        var view = thymeleafViewResolver.resolveViewName(viewName, java.util.Locale.ENGLISH);
        var response = new MockHttpServletResponse();
        assertThat(view).isNotNull();
        view.render(java.util.Map.of(), new MockHttpServletRequest(), response);

        assertThat(response.getContentAsString()).contains("Go to Homepage");
    }
}
