package kz.clientflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;
import kz.clientflow.IntegrationTest;

class AccountTests extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    private TestUser user;

    @BeforeEach
    void setUp() throws Exception {
        user = signUp("user@example.com");
    }

    private Cookie secondSession() throws Exception {
        return login("user@example.com", PASSWORD).getResponse().getCookie("CF_SESSION");
    }

    // ------------------------------------------------------------------ profile

    @Test
    void profileAndPreferencesArePersisted() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").with(csrf()).cookie(user.session()).contentType("application/json")
                        .content(json("{'fullName':'Aigerim B.','preferredLanguage':'kk','theme':'DARK',"
                                + "'email':'hacker@example.com','emailVerified':false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aigerim B."))
                .andExpect(jsonPath("$.preferredLanguage").value("kk"))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.email").value("user@example.com"));

        mockMvc.perform(patch("/api/v1/users/me").with(csrf()).cookie(user.session()).contentType("application/json")
                        .content(json("{'preferredLanguage':'de'}")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/users/me/notification-settings").with(csrf()).cookie(user.session())
                        .contentType("application/json")
                        .content(json("{'deadlines':false,'overdue':true,'orderChanges':false,'payments':true}")))
                .andExpect(jsonPath("$.notifications.deadlines").value(false))
                .andExpect(jsonPath("$.notifications.payments").value(true));
        assertThat(jdbc.queryForObject("SELECT notify_order_changes FROM users WHERE id = ?", Boolean.class,
                user.id())).isFalse();
    }

    // ------------------------------------------------------------------ password and sessions

    @Test
    void passwordChangeRequiresCurrentPasswordAndSignsOutOtherSessions() throws Exception {
        Cookie other = secondSession();

        mockMvc.perform(post("/api/v1/users/me/password").with(csrf()).cookie(user.session())
                        .contentType("application/json")
                        .content(json("{'currentPassword':'wrong-one','newPassword':'BrandNewPass9'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        var result = mockMvc.perform(post("/api/v1/users/me/password").with(csrf()).cookie(user.session())
                        .contentType("application/json")
                        .content(json("{'currentPassword':'%s','newPassword':'BrandNewPass9'}", PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();

        // The current session continues (with a new id), the other one is signed out
        Cookie renewed = result.getResponse().getCookie("CF_SESSION");
        mockMvc.perform(get("/api/v1/users/me").cookie(renewed != null ? renewed : user.session()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me").cookie(other)).andExpect(status().isUnauthorized());
        assertThat(login("user@example.com", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("user@example.com", "BrandNewPass9").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sessionsCanBeListedAndRevokedWithoutExposingSessionIds() throws Exception {
        Cookie other = secondSession();

        String list = mockMvc.perform(get("/api/v1/users/me/sessions").cookie(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(user.session().getValue()).doesNotContain(other.getValue());

        List<String> otherKeys = JsonPath.read(list, "$[?(@.current == false)].key");
        mockMvc.perform(delete("/api/v1/users/me/sessions/" + otherKeys.getFirst()).with(csrf())
                        .cookie(user.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(other)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").cookie(user.session())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/me/sessions/unknown").with(csrf()).cookie(user.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeOthersKeepsCurrentSession() throws Exception {
        Cookie other = secondSession();
        mockMvc.perform(post("/api/v1/users/me/sessions/revoke-others").with(csrf()).cookie(user.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(other)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").cookie(user.session())).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ avatar

    @Test
    void avatarIsValidatedByContentAndServedOnlyToColleagues() throws Exception {
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)).with(csrf()).cookie(user.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me/avatar").cookie(user.session()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/v1/users/me").cookie(user.session()))
                .andExpect(jsonPath("$.avatarUpdatedAt").isNotEmpty());

        // An HTML file renamed to .png is rejected: the type is detected from the bytes
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<script>alert(1)</script>".getBytes()))
                        .with(csrf()).cookie(user.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_INVALID_TYPE"));
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "big.png", "image/png", new byte[600 * 1024]))
                        .with(csrf()).cookie(user.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AVATAR_TOO_LARGE"));

        TestUser colleague = addMember(user, "colleague@example.com", "EMPLOYEE");
        TestUser outsider = signUp("outsider@example.com");
        mockMvc.perform(get(ws(user, "/members/" + user.id() + "/avatar")).cookie(colleague.session()))
                .andExpect(status().isOk());
        mockMvc.perform(get(ws(user, "/members/" + user.id() + "/avatar")).cookie(outsider.session()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/users/me/avatar").with(csrf()).cookie(user.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me/avatar").cookie(user.session())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ account deletion

    @Test
    void deletingAccountRemovesOwnWorkspaceButKeepsOthersData() throws Exception {
        // The user owns a workspace with a paid order...
        UUID client = createClient(user, "Client");
        UUID order = idOf(createOrder(user, client, null));
        mockMvc.perform(post(ws(user, "/orders/" + order + "/payments")).with(csrf()).cookie(user.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':100,'paymentMethod':'CASH','paymentDate':'%s'}",
                                LocalDate.now(ZoneId.of("Asia/Almaty")))))
                .andExpect(status().isCreated());
        // ...and is an employee in someone else's workspace with an assigned order
        TestUser boss = signUp("boss@example.com");
        mockMvc.perform(post(ws(boss, "/members")).with(csrf()).cookie(boss.session()).contentType("application/json")
                        .content(json("{'email':'user@example.com','role':'EMPLOYEE'}")))
                .andExpect(status().isAccepted());
        UUID bossOrder = idOf(createOrder(boss, createClient(boss, "Boss client"), user.id()));

        mockMvc.perform(delete("/api/v1/users/me").with(csrf()).cookie(user.session())
                        .contentType("application/json").content(json("{'password':'wrong-password'}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/users/me").with(csrf()).cookie(user.session())
                        .contentType("application/json").content(json("{'password':'%s'}", PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'user@example.com'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspaces WHERE id = ?", Integer.class,
                user.workspaceId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
        // The other workspace keeps its order, now unassigned, and its history
        mockMvc.perform(get(ws(boss, "/orders/" + bossOrder)).cookie(boss.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUser").doesNotExist());
        mockMvc.perform(get(ws(boss, "/members")).cookie(boss.session()))
                .andExpect(jsonPath("$.members.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE metadata->>'email' = "
                + "'user@example.com'", Integer.class)).isZero();
        // All sessions are gone
        mockMvc.perform(get("/api/v1/users/me").cookie(user.session())).andExpect(status().isUnauthorized());
    }
}
