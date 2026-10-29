package kz.clientflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 2 checks: the application starts on a real PostgreSQL, Flyway creates
 * the schema, and the baseline security rules are active.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesAllTablesInClientflowSchema() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'clientflow'",
                String.class);

        assertThat(tables).contains(
                "users", "workspaces", "workspace_memberships", "clients", "customer_orders",
                "order_notes", "payments", "notifications", "activity_logs",
                "verification_tokens", "password_reset_tokens",
                "spring_session", "spring_session_attributes");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class))
                .isZero();
    }

    @Test
    void databaseRejectsNonPositivePayments() {
        Integer checks = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.check_constraints "
                        + "WHERE constraint_schema = 'clientflow' AND check_clause LIKE '%amount > %'",
                Integer.class);
        assertThat(checks).isGreaterThanOrEqualTo(2);
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointReturns401WithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/clients").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void requestsFromThePublicFrontendOriginPassCors() throws Exception {
        // Browsers send Origin on POST; the configured public URL must be accepted
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Origin", "http://localhost:5173")
                        .contentType("application/json").content("{\"email\":\"x@example.com\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .header("Origin", "https://evil.example")
                        .contentType("application/json").content("{\"email\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());
    }
}
