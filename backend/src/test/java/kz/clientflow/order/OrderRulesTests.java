package kz.clientflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import kz.clientflow.IntegrationTest;

class OrderRulesTests extends IntegrationTest {

    private TestUser owner;
    private UUID client;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        client = createClient(owner, "Client");
    }

    private ResultActions changeStatus(UUID orderId, String status, long version) throws Exception {
        return mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/status")).with(csrf()).cookie(owner.session())
                .contentType("application/json").content(json("{'status':'%s','version':%d}", status, version)));
    }

    /** Moves a new order through the given statuses and returns the last response. */
    private String moveThrough(UUID orderId, String... statuses) throws Exception {
        String body = null;
        long version = 0;
        for (String status : statuses) {
            body = changeStatus(orderId, status, version).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            version = versionOf(body);
        }
        return body;
    }

    // ------------------------------------------------------------------ status transitions

    @Test
    void fullLifeCycleIsRecordedInHistory() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        moveThrough(id, "CONFIRMED", "IN_PROGRESS", "COMPLETED");

        mockMvc.perform(get(ws(owner, "/orders/" + id + "/activity")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].action").value("ORDER_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].metadata.from").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].metadata.to").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(owner.id().toString()))
                .andExpect(jsonPath("$.items[3].action").value("ORDER_CREATED"));
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "NEW, IN_PROGRESS", "NEW, COMPLETED", "CONFIRMED, COMPLETED", "CONFIRMED, NEW",
            "IN_PROGRESS, NEW", "COMPLETED, CANCELLED", "CANCELLED, NEW", "NEW, NEW"})
    void invalidTransitionsAreRejected(String from, String to) throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        long version = 0;
        String body = switch (from) {
            case "CONFIRMED" -> moveThrough(id, "CONFIRMED");
            case "IN_PROGRESS" -> moveThrough(id, "CONFIRMED", "IN_PROGRESS");
            case "COMPLETED" -> moveThrough(id, "CONFIRMED", "IN_PROGRESS", "COMPLETED");
            case "CANCELLED" -> moveThrough(id, "CANCELLED");
            default -> null;
        };
        if (body != null) {
            version = versionOf(body);
        }

        changeStatus(id, to, version)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void inProgressOrderCanBeCancelled() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        String body = moveThrough(id, "CONFIRMED", "IN_PROGRESS", "CANCELLED");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("CANCELLED");
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        changeStatus(id, "CONFIRMED", 0).andExpect(status().isOk());

        // A second tab still has version 0
        changeStatus(id, "CANCELLED", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    // ------------------------------------------------------------------ editing and archiving

    @Test
    void finishedOrdersAreReadOnlyAndCanBeArchived() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        mockMvc.perform(post(ws(owner, "/orders/" + id + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FINISHED"));

        String completed = moveThrough(id, "CONFIRMED", "IN_PROGRESS", "COMPLETED");
        mockMvc.perform(put(ws(owner, "/orders/" + id)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'X','totalAmount':1,'currency':'KZT','version':%d}",
                                versionOf(completed))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));

        mockMvc.perform(post(ws(owner, "/orders/" + id + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get(ws(owner, "/orders")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(owner, "/orders")).param("archive", "ARCHIVED").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void updateChangesAllowedFieldsAndLogsThem() throws Exception {
        String order = createOrder(owner, client, null);
        String deadline = LocalDate.now(ZoneId.of("Asia/Almaty")).plusDays(3).toString();

        mockMvc.perform(put(ws(owner, "/orders/" + idOf(order))).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Mobile app','totalAmount':200000,'currency':'USD',"
                                + "'deadline':'%s','version':0,'orderNumber':'HACK-1','status':'COMPLETED'}",
                                deadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mobile app"))
                .andExpect(jsonPath("$.totalAmount").value(200000))
                .andExpect(jsonPath("$.orderNumber").value("ORD-000001"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(jdbc.queryForObject("SELECT metadata->>'changedFields' FROM activity_logs "
                + "WHERE action = 'ORDER_UPDATED'", String.class))
                .contains("title", "totalAmount", "currency", "deadline");
    }

    // ------------------------------------------------------------------ validation

    @Test
    void amountsAndDeadlinesAreValidated() throws Exception {
        String base = "{'clientId':'%s','title':'X','currency':'KZT',";
        for (String amount : new String[] {"0", "-5", "10.123", "1000000000000"}) {
            mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                            .contentType("application/json")
                            .content(json(base + "'totalAmount':%s}", client, amount)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json(base + "'totalAmount':10,'deadline':'2020-01-01'}", client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEADLINE_IN_PAST"));
        mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'X','currency':'BTC','totalAmount':10}", client)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersCannotBeCreatedForArchivedClient() throws Exception {
        mockMvc.perform(post(ws(owner, "/clients/" + client + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(status().isOk());

        mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'X','totalAmount':10,'currency':'KZT'}", client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ARCHIVED"));
    }

    @Test
    void overdueOrdersAreDetected() throws Exception {
        UUID late = idOf(createOrder(owner, client, null));
        UUID done = idOf(createOrder(owner, client, null));
        moveThrough(done, "CONFIRMED", "IN_PROGRESS", "COMPLETED");
        jdbc.update("UPDATE customer_orders SET deadline = current_date - 5");

        mockMvc.perform(get(ws(owner, "/orders")).param("overdue", "true").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(late.toString()))
                .andExpect(jsonPath("$.items[0].overdue").value(true));
    }

    // ------------------------------------------------------------------ numbering

    @Test
    void orderNumbersAreUniqueAndSequentialEvenInParallel() throws Exception {
        int count = 8;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> JsonPath.read(createOrder(owner, client, null), "$.orderNumber"));
        }
        List<String> numbers = new ArrayList<>();
        for (Future<String> f : pool.invokeAll(tasks)) {
            numbers.add(f.get());
        }
        pool.shutdown();

        assertThat(new HashSet<>(numbers)).hasSize(count);
        assertThat(numbers).allMatch(n -> n.matches("ORD-00000[1-8]"));
    }

    @Test
    void numbersAreCountedPerWorkspace() throws Exception {
        TestUser other = signUp("other@example.com");
        UUID otherClient = createClient(other, "C");

        assertThat((String) JsonPath.read(createOrder(owner, client, null), "$.orderNumber")).isEqualTo("ORD-000001");
        assertThat((String) JsonPath.read(createOrder(other, otherClient, null), "$.orderNumber"))
                .isEqualTo("ORD-000001");
    }

    // ------------------------------------------------------------------ board, notes, search

    @Test
    void boardGroupsOrdersByStatus() throws Exception {
        moveThrough(idOf(createOrder(owner, client, null)), "CONFIRMED");
        createOrder(owner, client, null);

        mockMvc.perform(get(ws(owner, "/orders/board")).cookie(owner.session()))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].total").value(1));
    }

    @Test
    void notesAreAddedAndListed() throws Exception {
        UUID id = idOf(createOrder(owner, client, null));
        mockMvc.perform(post(ws(owner, "/orders/" + id + "/notes")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'body':'Call the client on Monday'}")))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ws(owner, "/orders/" + id + "/notes")).cookie(owner.session()))
                .andExpect(jsonPath("$[0].body").value("Call the client on Monday"))
                .andExpect(jsonPath("$[0].author.id").value(owner.id().toString()));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        createOrder(owner, client, null);

        mockMvc.perform(get(ws(owner, "/orders")).param("search", "web").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get(ws(owner, "/orders")).param("search", "%").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(owner, "/orders")).param("search", "ORD-000001").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
