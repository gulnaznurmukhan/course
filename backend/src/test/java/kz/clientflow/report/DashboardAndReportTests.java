package kz.clientflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kz.clientflow.IntegrationTest;

class DashboardAndReportTests extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Almaty"));

    private TestUser owner;
    private UUID client;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        client = createClient(owner, "Client");
    }

    private void pay(UUID orderId, String amount, LocalDate date) throws Exception {
        mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/payments")).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':%s,'paymentMethod':'CARD','paymentDate':'%s'}", amount, date)))
                .andExpect(status().isCreated());
    }

    private UUID order(String amount, String currency) throws Exception {
        return idOf(mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'Job','totalAmount':%s,'currency':'%s'}",
                                client, amount, currency)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void setStatus(UUID orderId, String... statuses) throws Exception {
        int version = 0;
        for (String s : statuses) {
            mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/status")).with(csrf()).cookie(owner.session())
                            .contentType("application/json").content(json("{'status':'%s','version':%d}", s, version++)))
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void dashboardShowsRealNumbersGroupedByCurrency() throws Exception {
        UUID kzt = order("1000", "KZT");
        UUID usd = order("200", "USD");
        UUID late = order("50", "KZT");
        setStatus(usd, "CONFIRMED", "IN_PROGRESS", "COMPLETED");
        pay(kzt, "300", TODAY);
        pay(usd, "150", TODAY);
        jdbc.update("UPDATE customer_orders SET deadline = current_date - 3 WHERE id = ?", late);
        jdbc.update("UPDATE customer_orders SET deadline = current_date + 2 WHERE id = ?", kzt);

        mockMvc.perform(get(ws(owner, "/dashboard")).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClients").value(1))
                .andExpect(jsonPath("$.activeOrders").value(2))
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.overdueOrders").value(1))
                .andExpect(jsonPath("$.ordersByStatus.NEW").value(2))
                .andExpect(jsonPath("$.ordersByStatus.COMPLETED").value(1))
                .andExpect(jsonPath("$.paymentsReceived[0].currency").value("KZT"))
                .andExpect(jsonPath("$.paymentsReceived[0].amount").value(300))
                .andExpect(jsonPath("$.paymentsReceived[1].currency").value("USD"))
                .andExpect(jsonPath("$.paymentsReceived[1].amount").value(150))
                // KZT: (1000 - 300) + 50 ; USD: 200 - 150
                .andExpect(jsonPath("$.outstandingBalances[0].amount").value(750))
                .andExpect(jsonPath("$.outstandingBalances[1].amount").value(50))
                .andExpect(jsonPath("$.upcomingDeadlines[0].id").value(kzt.toString()))
                .andExpect(jsonPath("$.recentActivity").isArray());
    }

    @Test
    void dashboardPaymentPeriodIsApplied() throws Exception {
        UUID id = order("1000", "KZT");
        pay(id, "100", TODAY.minusDays(40));
        pay(id, "200", TODAY);

        mockMvc.perform(get(ws(owner, "/dashboard")).param("from", TODAY.minusDays(1).toString())
                        .param("to", TODAY.toString()).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentsReceived[0].amount").value(200));
        mockMvc.perform(get(ws(owner, "/dashboard")).param("from", TODAY.minusDays(60).toString())
                        .param("to", TODAY.toString()).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentsReceived[0].amount").value(300));
        mockMvc.perform(get(ws(owner, "/dashboard")).param("from", TODAY.toString())
                        .param("to", TODAY.minusDays(1).toString()).cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void employeeDashboardCoversOnlyAssignedOrders() throws Exception {
        TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        createOrder(owner, client, employee.id());
        createOrder(owner, client, null);

        mockMvc.perform(get(ws(owner, "/dashboard")).cookie(employee.session()))
                .andExpect(jsonPath("$.onlyAssignedOrders").value(true))
                .andExpect(jsonPath("$.activeOrders").value(1))
                .andExpect(jsonPath("$.recentActivity").doesNotExist());
        mockMvc.perform(get(ws(owner, "/dashboard")).cookie(owner.session()))
                .andExpect(jsonPath("$.activeOrders").value(2));
    }

    // ------------------------------------------------------------------ reports

    @Test
    void orderReportFiltersAndSummarises() throws Exception {
        UUID paid = order("100", "KZT");
        UUID partial = order("300", "KZT");
        order("500", "KZT");
        pay(paid, "100", TODAY);
        pay(partial, "50", TODAY);

        mockMvc.perform(get(ws(owner, "/reports/orders")).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.summary[0].count").value(3))
                .andExpect(jsonPath("$.summary[0].totalAmount").value(900))
                .andExpect(jsonPath("$.summary[0].paidAmount").value(150))
                .andExpect(jsonPath("$.summary[0].balance").value(750));
        mockMvc.perform(get(ws(owner, "/reports/orders")).param("paymentState", "PARTIALLY_PAID")
                        .cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(partial.toString()))
                .andExpect(jsonPath("$.items[0].balance").value(250));
        mockMvc.perform(get(ws(owner, "/reports/orders")).param("from", TODAY.minusDays(30).toString())
                        .param("to", TODAY.minusDays(1).toString()).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(owner, "/reports/orders")).param("clientId", UUID.randomUUID().toString())
                        .cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(owner, "/reports/orders")).param("size", "2").param("page", "1")
                        .cookie(owner.session()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void paymentReportListsVoidedPaymentsButDoesNotCountThem() throws Exception {
        UUID id = order("1000", "KZT");
        pay(id, "100", TODAY);
        pay(id, "200", TODAY);
        UUID voided = jdbc.queryForObject("SELECT id FROM payments WHERE amount = 100", UUID.class);
        mockMvc.perform(post(ws(owner, "/orders/" + id + "/payments/" + voided + "/void")).with(csrf())
                        .cookie(owner.session()).contentType("application/json").content(json("{'reason':'Refund'}")))
                .andExpect(status().isOk());

        mockMvc.perform(get(ws(owner, "/reports/payments")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.summary[0].amount").value(200))
                .andExpect(jsonPath("$.summary[0].count").value(1));
        mockMvc.perform(get(ws(owner, "/reports/payments")).param("method", "CASH").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void csvExportIsUtf8WithHeaderAndNeutralisesFormulas() throws Exception {
        UUID evil = createClient(owner, "=HYPERLINK(\\\"http://evil\\\")");
        mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'Сайт, \\\"новый\\\"','totalAmount':10,'currency':'KZT'}",
                                evil)))
                .andExpect(status().isCreated());

        byte[] body = mockMvc.perform(get(ws(owner, "/reports/orders.csv")).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"orders_")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿order_number,created_date,client");
        assertThat(csv).contains("ORD-000001");
        assertThat(csv).contains("\"Сайт, \"\"новый\"\"\"");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
    }

    @Test
    void reportsAreForManagersOnlyAndWorkspaceScoped() throws Exception {
        TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        TestUser outsider = signUp("outsider@example.com");

        mockMvc.perform(get(ws(owner, "/reports/orders")).cookie(employee.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ws(owner, "/reports/orders.csv")).cookie(employee.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ws(owner, "/reports/payments.csv")).cookie(outsider.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/dashboard")).cookie(outsider.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/reports/orders.csv"))).andExpect(status().isUnauthorized());
    }
}
