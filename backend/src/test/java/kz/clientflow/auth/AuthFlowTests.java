package kz.clientflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import kz.clientflow.IntegrationTest;

class AuthFlowTests extends IntegrationTest {

    private static final String EMAIL = "aigerim@example.com";

    // ---------------------------------------------------------------- registration

    @Test
    void registrationCreatesUserWithPersonalWorkspaceAndSendsVerificationEmail() throws Exception {
        register("Aigerim@Example.com");

        assertThat(jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE email = ?", Boolean.class, EMAIL)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT m.role FROM workspace_memberships m JOIN users u ON u.id = m.user_id
                WHERE u.email = ?""", String.class, EMAIL)).isEqualTo("OWNER");

        SimpleMailMessage email = sentEmails().getLast();
        assertThat(email.getTo()).containsExactly(EMAIL);
        assertThat(email.getText()).contains("http://localhost:5173/verify-email?token=");
    }

    @Test
    void passwordIsStoredAsArgon2idHashAndTokenOnlyAsHash() throws Exception {
        register(EMAIL);
        String rawToken = tokenFromLastEmail();

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, EMAIL);
        assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM verification_tokens WHERE token_hash = ?", Integer.class, rawToken)).isZero();
    }

    @Test
    void registeringExistingEmailGivesSameResponseAndNoDuplicate() throws Exception {
        register(EMAIL);
        register(EMAIL);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspaces", Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationIgnoresInjectedVerificationFlag() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                        .content(json("{'fullName':'X','email':'%s','password':'%s','emailVerified':true}",
                                EMAIL, PASSWORD)))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject(
                "SELECT email_verified FROM users WHERE email = ?", Boolean.class, EMAIL)).isFalse();
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json")
                        .content(json("{'fullName':'','email':'not-an-email','password':'short'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json("{'email':'%s','password':'%s'}", EMAIL, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- verification

    @Test
    void unverifiedUserCannotSignIn() throws Exception {
        register(EMAIL);

        MvcResult result = login(EMAIL, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("EMAIL_NOT_VERIFIED");
        assertThat(result.getResponse().getCookie("CF_SESSION")).isNull();
    }

    @Test
    void verificationTokenCanBeUsedOnlyOnce() throws Exception {
        register(EMAIL);
        String token = tokenFromLastEmail();
        verifyEmail(token);

        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType("application/json")
                        .content(json("{'token':'%s'}", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        register(EMAIL);
        String token = tokenFromLastEmail();
        jdbc.update("UPDATE verification_tokens SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType("application/json")
                        .content(json("{'token':'%s'}", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void resendingVerificationInvalidatesPreviousLink() throws Exception {
        register(EMAIL);
        String oldToken = tokenFromLastEmail();

        mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf()).contentType("application/json")
                        .content(json("{'email':'%s'}", EMAIL)))
                .andExpect(status().isAccepted());
        String newToken = tokenFromLastEmail();

        assertThat(newToken).isNotEqualTo(oldToken);
        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf()).contentType("application/json")
                        .content(json("{'token':'%s'}", oldToken)))
                .andExpect(status().isBadRequest());
        verifyEmail(newToken);
    }

    // ---------------------------------------------------------------- login / logout

    @Test
    void verifiedUserSignsInAndOut() throws Exception {
        Cookie session = registerVerifiedAndLogin(EMAIL);

        mockMvc.perform(get("/api/v1/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.workspaces[0].role").value("OWNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionCookieIsHttpOnlyAndSameSite() throws Exception {
        register(EMAIL);
        verifyEmail(tokenFromLastEmail());

        String setCookie = login(EMAIL, PASSWORD).getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("CF_SESSION=")).findFirst().orElseThrow();

        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        registerVerifiedAndLogin(EMAIL);

        MvcResult wrongPassword = login(EMAIL, "WrongPassword1!");
        MvcResult unknownEmail = login("nobody@example.com", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
        assertThat(unknownEmail.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        registerVerifiedAndLogin(EMAIL);

        for (int i = 0; i < 9; i++) {
            assertThat(login(EMAIL, "WrongPassword1!").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = login(EMAIL, PASSWORD);

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString()).contains("RATE_LIMITED");
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void forgotPasswordForUnknownEmailLooksTheSameAndSendsNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").with(csrf()).contentType("application/json")
                        .content(json("{'email':'nobody@example.com'}")))
                .andExpect(status().isAccepted());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void passwordResetChangesPasswordRevokesSessionsAndCannotBeReused() throws Exception {
        Cookie oldSession = registerVerifiedAndLogin(EMAIL);

        mockMvc.perform(post("/api/v1/auth/forgot-password").with(csrf()).contentType("application/json")
                        .content(json("{'email':'%s'}", EMAIL)))
                .andExpect(status().isAccepted());
        String token = tokenFromLastEmail();
        assertThat(sentEmails().getLast().getText()).contains("/reset-password?token=");

        MvcResult reset = mockMvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType("application/json")
                        .content(json("{'token':'%s','newPassword':'BrandNewPass9'}", token)))
                .andExpect(status().isNoContent())
                .andReturn();

        // Not signed in automatically, and the old session no longer works
        assertThat(reset.getResponse().getCookie("CF_SESSION")).isNull();
        mockMvc.perform(get("/api/v1/users/me").cookie(oldSession)).andExpect(status().isUnauthorized());

        // The token cannot be used twice
        mockMvc.perform(post("/api/v1/auth/reset-password").with(csrf()).contentType("application/json")
                        .content(json("{'token':'%s','newPassword':'AnotherPass9'}", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(EMAIL, "BrandNewPass9").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void expiredPasswordResetTokenIsRejected() throws Exception {
        registerVerifiedAndLogin(EMAIL);
        mockMvc.perform(post("/api/v1/auth/forgot-password").with(csrf()).contentType("application/json")
                .content(json("{'email':'%s'}", EMAIL)));
        String token = tokenFromLastEmail();
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/api/v1/auth/reset-password").with(csrf()).contentType("application/json")
                        .content(json("{'token':'%s','newPassword':'BrandNewPass9'}", token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertThat(login(EMAIL, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }
}
