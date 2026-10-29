package kz.clientflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import kz.clientflow.IntegrationTest;

/** Financial rules: balance calculation, overpayment, concurrency, idempotency, voiding. */
class PaymentTests extends IntegrationTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Almaty")).toString();

    private TestUser owner;
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        UUID client = createClient(owner, "Client");
        orderId = idOf(createOrder(owner, client, null)); // total 150000.50 KZT
    }

    private String paymentsUrl() {
        return ws(owner, "/orders/" + orderId + "/payments");
    }

    private ResultActions pay(TestUser user, String amount, UUID key) throws Exception {
        return mockMvc.perform(post(paymentsUrl()).with(csrf()).cookie(user.session())
                .header("Idempotency-Key", key.toString())
                .contentType("application/json")
                .content(json("{'amount':%s,'paymentMethod':'CASH','paymentDate':'%s','reference':'R-1'}",
                        amount, TODAY)));
    }

    private ResultActions pay(String amount) throws Exception {
        return pay(owner, amount, UUID.randomUUID());
    }

    private BigDecimal decimal(MvcResult result, String path) throws Exception {
        return new BigDecimal(JsonPath.read(result.getResponse().getContentAsString(), path).toString());
    }

    // ------------------------------------------------------------------ calculations

    @Test
    void partialAndFullPaymentsUpdateBalanceAndState() throws Exception {
        mockMvc.perform(get(paymentsUrl()).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentState").value("UNPAID"))
                .andExpect(jsonPath("$.balance").value(150000.50));

        pay("50000.25").andExpect(status().isCreated());
        MvcResult partial = mockMvc.perform(get(paymentsUrl()).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentState").value("PARTIALLY_PAID"))
                .andReturn();
        assertThat(decimal(partial, "$.paidAmount")).isEqualByComparingTo("50000.25");
        assertThat(decimal(partial, "$.balance")).isEqualByComparingTo("100000.25");

        pay("100000.25").andExpect(status().isCreated());
        MvcResult full = mockMvc.perform(get(ws(owner, "/orders/" + orderId)).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentState").value("PAID"))
                .andReturn();
        assertThat(decimal(full, "$.balance")).isEqualByComparingTo("0");
    }

    @Test
    void decimalAmountsAreExact() throws Exception {
        // 0.1 + 0.2 must be exactly 0.3 (no floating point errors)
        pay("0.10").andExpect(status().isCreated());
        pay("0.20").andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get(paymentsUrl()).cookie(owner.session())).andReturn();
        assertThat(decimal(result, "$.paidAmount")).isEqualByComparingTo("0.30");
        assertThat(decimal(result, "$.balance")).isEqualByComparingTo("150000.20");
    }

    // ------------------------------------------------------------------ limits and validation

    @Test
    void overpaymentIsRejected() throws Exception {
        pay("150000.00").andExpect(status().isCreated());

        pay("0.51")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OVERPAYMENT"));
        pay("0.50").andExpect(status().isCreated());
        pay("0.01").andExpect(status().isConflict());
    }

    @Test
    void invalidPaymentsAreRejected() throws Exception {
        for (String amount : new String[] {"0", "-1", "10.001"}) {
            pay(amount).andExpect(status().isBadRequest());
        }
        // Missing Idempotency-Key header
        mockMvc.perform(post(paymentsUrl()).with(csrf()).cookie(owner.session()).contentType("application/json")
                        .content(json("{'amount':10,'paymentMethod':'CASH','paymentDate':'%s'}", TODAY)))
                .andExpect(status().isBadRequest());
        // Future date
        mockMvc.perform(post(paymentsUrl()).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':10,'paymentMethod':'CASH','paymentDate':'2999-01-01'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_DATE_IN_FUTURE"));
        // Unknown method
        mockMvc.perform(post(paymentsUrl()).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':10,'paymentMethod':'CRYPTO','paymentDate':'%s'}", TODAY)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ duplicates and concurrency

    @Test
    void duplicateSubmissionWithSameKeyCreatesOnePayment() throws Exception {
        UUID key = UUID.randomUUID();
        String first = pay(owner, "1000", key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = pay(owner, "1000", key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void parallelDuplicateSubmissionsCreateOnePayment() throws Exception {
        UUID key = UUID.randomUUID();
        List<Integer> statuses = runInParallel(6, () -> pay(owner, "1000", key).andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(200, 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentPaymentsCannotExceedTheOrderTotal() throws Exception {
        // Each payment is 60% of the total: only one of them may succeed
        List<Integer> statuses = runInParallel(6, () -> pay("90000").andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM payments", BigDecimal.class))
                .isEqualByComparingTo("90000");
    }

    private List<Integer> runInParallel(int count, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                tasks.add(task);
            }
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    // ------------------------------------------------------------------ corrections

    @Test
    void voidedPaymentStaysInHistoryButNoLongerCounts() throws Exception {
        String payment = pay("150000.50").andReturn().getResponse().getContentAsString();
        String voidUrl = paymentsUrl() + "/" + idOf(payment) + "/void";

        mockMvc.perform(post(voidUrl).with(csrf()).cookie(owner.session()).contentType("application/json")
                        .content(json("{'reason':'Wrong amount entered'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true))
                .andExpect(jsonPath("$.voidedBy.id").value(owner.id().toString()));

        mockMvc.perform(get(paymentsUrl()).cookie(owner.session()))
                .andExpect(jsonPath("$.paymentState").value("UNPAID"))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].voidReason").value("Wrong amount entered"));

        // Cannot be voided twice; the correct payment can now be recorded
        mockMvc.perform(post(voidUrl).with(csrf()).cookie(owner.session()).contentType("application/json")
                        .content(json("{'reason':'again'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_VOIDED"));
        pay("150000.50").andExpect(status().isCreated());

        mockMvc.perform(get(ws(owner, "/orders/" + orderId + "/activity")).cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].action").value("PAYMENT_RECORDED"))
                .andExpect(jsonPath("$.items[1].action").value("PAYMENT_VOIDED"))
                .andExpect(jsonPath("$.items[1].metadata.reason").value("Wrong amount entered"));
    }

    @Test
    void voidRequiresReason() throws Exception {
        String payment = pay("100").andReturn().getResponse().getContentAsString();
        mockMvc.perform(post(paymentsUrl() + "/" + idOf(payment) + "/void").with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'reason':' '}")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ interaction with orders

    @Test
    void orderTotalCannotDropBelowPaidAmountAndCurrencyIsLocked() throws Exception {
        pay("100000").andExpect(status().isCreated());

        mockMvc.perform(put(ws(owner, "/orders/" + orderId)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Website','totalAmount':99999.99,'currency':'KZT','version':0}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOTAL_BELOW_PAID"));
        mockMvc.perform(put(ws(owner, "/orders/" + orderId)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Website','totalAmount':150000.50,'currency':'USD','version':0}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CURRENCY_LOCKED"));
        mockMvc.perform(put(ws(owner, "/orders/" + orderId)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Website','totalAmount':100000,'currency':'KZT','version':0}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentState").value("PAID"));
    }

    @Test
    void orderWithPaymentsCannotBeCancelledUntilTheyAreVoided() throws Exception {
        String payment = pay("500").andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/status")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'status':'CANCELLED','version':0}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_HAS_PAYMENTS"));

        mockMvc.perform(post(paymentsUrl() + "/" + idOf(payment) + "/void").with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'reason':'Refunded'}")))
                .andExpect(status().isOk());
        mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/status")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'status':'CANCELLED','version':0}")))
                .andExpect(status().isOk());

        // Cancelled orders accept no payments
        pay("1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void ordersCanBeFilteredByPaymentState() throws Exception {
        UUID client = createClient(owner, "Second");
        UUID unpaid = idOf(createOrder(owner, client, null));
        pay("100").andExpect(status().isCreated());

        mockMvc.perform(get(ws(owner, "/orders")).param("paymentState", "PARTIALLY_PAID").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId.toString()));
        mockMvc.perform(get(ws(owner, "/orders")).param("paymentState", "UNPAID").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(unpaid.toString()));
        mockMvc.perform(get(ws(owner, "/orders")).param("paymentState", "PAID").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // ------------------------------------------------------------------ permissions and isolation

    @Test
    void employeeCanSeePaymentsOfAssignedOrderButNotRecordThem() throws Exception {
        TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        UUID client = createClient(owner, "C2");
        UUID assigned = idOf(createOrder(owner, client, employee.id()));

        mockMvc.perform(get(ws(owner, "/orders/" + assigned + "/payments")).cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRecord").value(false));
        mockMvc.perform(get(paymentsUrl()).cookie(employee.session())).andExpect(status().isNotFound());
        mockMvc.perform(post(ws(owner, "/orders/" + assigned + "/payments")).with(csrf()).cookie(employee.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':10,'paymentMethod':'CASH','paymentDate':'%s'}", TODAY)))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotPayOrVoidForeignOrders() throws Exception {
        String payment = pay("100").andReturn().getResponse().getContentAsString();
        TestUser outsider = signUp("outsider@example.com");

        pay(outsider, "10", UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(post(paymentsUrl() + "/" + idOf(payment) + "/void").with(csrf()).cookie(outsider.session())
                        .contentType("application/json").content(json("{'reason':'x'}")))
                .andExpect(status().isNotFound());
        // Foreign order id inside the outsider's own workspace
        mockMvc.perform(get(ws(outsider, "/orders/" + orderId + "/payments")).cookie(outsider.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherOrder() throws Exception {
        UUID key = UUID.randomUUID();
        pay(owner, "10", key).andExpect(status().isCreated());
        UUID other = idOf(createOrder(owner, createClient(owner, "Other"), null));

        mockMvc.perform(post(ws(owner, "/orders/" + other + "/payments")).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", key.toString()).contentType("application/json")
                        .content(json("{'amount':10,'paymentMethod':'CASH','paymentDate':'%s'}", TODAY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
}
