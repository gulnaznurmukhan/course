package kz.clientflow;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

/**
 * Base class for API tests: real PostgreSQL (Testcontainers), MockMvc,
 * a captured mail sender and helpers for common auth flows.
 * Every test starts with empty tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.scheduler.enabled=false", "app.cron-secret=test-cron-secret"})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Str0ngPassw0rd!";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE users, workspaces, rate_limits, spring_session CASCADE");
        clearInvocations(mailSender);
    }

    protected static String json(String template, Object... args) {
        return template.formatted(args).replace('\'', '"');
    }

    protected void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(json("{'fullName':'Test User','email':'%s','password':'%s','language':'en'}",
                                email, PASSWORD)))
                .andExpect(status().isAccepted());
    }

    /** Returns all emails sent so far. */
    protected List<SimpleMailMessage> sentEmails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    /** Extracts the token from the link in the most recent email. */
    protected String tokenFromLastEmail() {
        List<SimpleMailMessage> emails = sentEmails();
        Matcher matcher = TOKEN.matcher(emails.getLast().getText());
        if (!matcher.find()) {
            throw new AssertionError("No token link in the email");
        }
        return matcher.group(1);
    }

    protected void verifyEmail(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                        .contentType("application/json").content(json("{'token':'%s'}", token)))
                .andExpect(status().isNoContent());
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType("application/json")
                        .content(json("{'email':'%s','password':'%s'}", email, password)))
                .andReturn();
    }

    /** Registers, verifies and signs in a user; returns the session cookie. */
    protected Cookie registerVerifiedAndLogin(String email) throws Exception {
        register(email);
        verifyEmail(tokenFromLastEmail());
        MvcResult result = login(email, PASSWORD);
        Cookie session = result.getResponse().getCookie("CF_SESSION");
        if (result.getResponse().getStatus() != 200 || session == null) {
            throw new AssertionError("Login failed: " + result.getResponse().getStatus());
        }
        return session;
    }

    // ------------------------------------------------------------------ business helpers

    /** A signed-in test user with their personal workspace. */
    protected record TestUser(Cookie session, UUID id, UUID workspaceId) {
    }

    protected TestUser signUp(String email) throws Exception {
        Cookie session = registerVerifiedAndLogin(email);
        String me = mockMvc.perform(get("/api/v1/users/me").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new TestUser(session, UUID.fromString(JsonPath.read(me, "$.id")),
                UUID.fromString(JsonPath.read(me, "$.workspaces[0].id")));
    }

    /** Signs up a new user and adds them to the owner's workspace with the given role. */
    protected TestUser addMember(TestUser owner, String email, String role) throws Exception {
        TestUser member = signUp(email);
        mockMvc.perform(post(ws(owner, "/members")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'email':'%s','role':'%s'}", email, role)))
                .andExpect(status().isAccepted());
        return member;
    }

    /** URL inside the user's own workspace. */
    protected static String ws(TestUser owner, String path) {
        return "/api/v1/workspaces/" + owner.workspaceId() + path;
    }

    protected UUID createClient(TestUser user, String name) throws Exception {
        String body = mockMvc.perform(post(ws(user, "/clients")).with(csrf()).cookie(user.session())
                        .contentType("application/json").content(json("{'name':'%s'}", name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** Creates an order in the user's workspace; assignee may be null. Returns the response JSON. */
    protected String createOrder(TestUser user, UUID clientId, UUID assigneeId) throws Exception {
        String assignee = assigneeId == null ? "null" : "'" + assigneeId + "'";
        return mockMvc.perform(post(ws(user, "/orders")).with(csrf()).cookie(user.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'Website','totalAmount':150000.50,"
                                + "'currency':'KZT','assignedUserId':%s}", clientId, assignee)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    protected static UUID idOf(String json) {
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    protected static int versionOf(String json) {
        return JsonPath.read(json, "$.version");
    }
}
