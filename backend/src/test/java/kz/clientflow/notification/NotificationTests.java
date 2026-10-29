package kz.clientflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import kz.clientflow.IntegrationTest;

class NotificationTests extends IntegrationTest {

    @Autowired
    MaintenanceJob job;

    private TestUser owner;
    private TestUser employee;
    private UUID client;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        client = createClient(owner, "Client");
    }

    private int count(String type, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = ? AND user_id = ?",
                Integer.class, type, userId);
    }

    // ------------------------------------------------------------------ reminders

    @Test
    void remindersAreCreatedOnceForTheRightPeople() throws Exception {
        UUID soon = idOf(createOrder(owner, client, employee.id()));
        UUID late = idOf(createOrder(owner, client, null));
        jdbc.update("UPDATE customer_orders SET deadline = current_date + 1 WHERE id = ?", soon);
        jdbc.update("UPDATE customer_orders SET deadline = current_date - 1 WHERE id = ?", late);

        job.run();
        job.run(); // running again must not duplicate anything

        assertThat(count("DEADLINE_APPROACHING", employee.id())).isEqualTo(1);
        assertThat(count("DEADLINE_APPROACHING", owner.id())).isZero(); // order has an assignee
        assertThat(count("ORDER_OVERDUE", owner.id())).isEqualTo(1);
        assertThat(count("ORDER_OVERDUE", employee.id())).isZero(); // not their order

        // A new deadline is a new reminder
        jdbc.update("UPDATE customer_orders SET deadline = current_date - 2 WHERE id = ?", late);
        job.run();
        assertThat(count("ORDER_OVERDUE", owner.id())).isEqualTo(2);
    }

    @Test
    void paymentReminderForCompletedOrderWithBalance() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        int version = 0;
        for (String s : new String[] {"CONFIRMED", "IN_PROGRESS", "COMPLETED"}) {
            mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(owner.session())
                    .contentType("application/json").content(json("{'status':'%s','version':%d}", s, version++)));
        }

        job.run();
        job.run();

        assertThat(count("PAYMENT_REMINDER", owner.id())).isEqualTo(1);
        mockMvc.perform(get(ws(owner, "/notifications")).cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].type").value("PAYMENT_REMINDER"))
                .andExpect(jsonPath("$.items[0].payload.balance").value("150000.50"))
                .andExpect(jsonPath("$.items[0].payload.currency").value("KZT"));
    }

    @Test
    void disabledPreferenceStopsReminders() throws Exception {
        UUID late = idOf(createOrder(owner, client, null));
        jdbc.update("UPDATE customer_orders SET deadline = current_date - 1 WHERE id = ?", late);
        jdbc.update("UPDATE users SET notify_overdue = false WHERE id = ?", owner.id());

        job.run();

        assertThat(count("ORDER_OVERDUE", owner.id())).isZero();
    }

    // ------------------------------------------------------------------ order changes

    @Test
    void assigneeIsNotifiedAboutChangesByOthersAndManagersAboutEmployeeProgress() throws Exception {
        String order = createOrder(owner, client, employee.id());
        UUID id = idOf(order);
        assertThat(count("ORDER_CHANGED", employee.id())).isEqualTo(1); // assigned

        mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'status':'CONFIRMED','version':0}")))
                .andExpect(status().isOk());
        assertThat(count("ORDER_CHANGED", employee.id())).isEqualTo(2);
        assertThat(count("ORDER_CHANGED", owner.id())).isZero(); // never about own changes

        mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(employee.session())
                        .contentType("application/json").content(json("{'status':'IN_PROGRESS','version':1}")))
                .andExpect(status().isOk());
        assertThat(count("ORDER_CHANGED", owner.id())).isEqualTo(1);
        assertThat(count("ORDER_CHANGED", employee.id())).isEqualTo(2);
    }

    // ------------------------------------------------------------------ API

    @Test
    void listReadAndReadAll() throws Exception {
        createOrder(owner, client, employee.id());
        createOrder(owner, client, employee.id());

        mockMvc.perform(get(ws(owner, "/notifications/unread-count")).cookie(employee.session()))
                .andExpect(jsonPath("$.count").value(2));
        String list = mockMvc.perform(get(ws(owner, "/notifications")).cookie(employee.session()))
                .andExpect(jsonPath("$.items[0].type").value("ORDER_CHANGED"))
                .andExpect(jsonPath("$.items[0].payload.change").value("ASSIGNED"))
                .andExpect(jsonPath("$.items[0].payload.actor").value("Test User"))
                .andReturn().getResponse().getContentAsString();
        String first = com.jayway.jsonpath.JsonPath.read(list, "$.items[0].id");

        mockMvc.perform(post(ws(owner, "/notifications/" + first + "/read")).with(csrf()).cookie(employee.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ws(owner, "/notifications")).param("unreadOnly", "true").cookie(employee.session()))
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(post(ws(owner, "/notifications/read-all")).with(csrf()).cookie(employee.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ws(owner, "/notifications/unread-count")).cookie(employee.session()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void usersCannotReadOthersNotifications() throws Exception {
        createOrder(owner, client, employee.id());
        String id = jdbc.queryForObject("SELECT id::text FROM notifications WHERE user_id = ?", String.class,
                employee.id());

        mockMvc.perform(post(ws(owner, "/notifications/" + id + "/read")).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/notifications")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // ------------------------------------------------------------------ cron endpoint

    @Test
    void cronEndpointRequiresTheSecret() throws Exception {
        mockMvc.perform(get("/api/v1/internal/cron/maintenance")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/internal/cron/maintenance").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());

        jdbc.update("INSERT INTO rate_limits (key, window_start, attempts) VALUES ('old', now() - interval '2 days', 1)");
        mockMvc.perform(get("/api/v1/internal/cron/maintenance").header("Authorization", "Bearer test-cron-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitsDeleted").value(1));
    }
}
