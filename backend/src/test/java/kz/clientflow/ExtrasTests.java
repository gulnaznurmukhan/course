package kz.clientflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Invoice, customer links, CSV import, revenue chart and calendar. */
class ExtrasTests extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Almaty"));

    private TestUser owner;
    private UUID client;
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        client = createClient(owner, "Kaspi Shop");
        orderId = idOf(createOrder(owner, client, null)); // 150000.50 KZT
    }

    private void pay(UUID order, String amount, LocalDate date) throws Exception {
        mockMvc.perform(post(ws(owner, "/orders/" + order + "/payments")).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':%s,'paymentMethod':'CARD','paymentDate':'%s'}", amount, date)))
                .andExpect(status().isCreated());
    }

    @Nested
    class Invoice {

        @Test
        void invoiceUsesRequisitesItemsAndCalculatedBalance() throws Exception {
            mockMvc.perform(put(ws(owner, "/requisites")).with(csrf()).cookie(owner.session())
                            .contentType("application/json")
                            .content(json("{'legalName':'ТОО «Студия»','bin':'123456789012','iban':'kz86 125k zt5004100100',"
                                    + "'bik':'KCJBKZKX','bankName':'Kaspi Bank'}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.iban").value("KZ86125KZT5004100100"));
            mockMvc.perform(put(ws(owner, "/orders/" + orderId + "/items")).with(csrf()).cookie(owner.session())
                            .contentType("application/json")
                            .content(json("{'version':0,'items':[{'name':'Design','quantity':2,'unitPrice':1000}]}")))
                    .andExpect(status().isOk());
            pay(orderId, "500", TODAY);

            mockMvc.perform(get(ws(owner, "/orders/" + orderId + "/invoice")).cookie(owner.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.number").value("ORD-000001"))
                    .andExpect(jsonPath("$.seller.legalName").value("ТОО «Студия»"))
                    .andExpect(jsonPath("$.seller.bin").value("123456789012"))
                    .andExpect(jsonPath("$.buyer.name").value("Kaspi Shop"))
                    .andExpect(jsonPath("$.lines[0].lineTotal").value(2000))
                    .andExpect(jsonPath("$.totalAmount").value(2000))
                    .andExpect(jsonPath("$.paidAmount").value(500))
                    .andExpect(jsonPath("$.balance").value(1500));
        }

        @Test
        void requisitesAreValidatedAndOwnerOnly() throws Exception {
            mockMvc.perform(put(ws(owner, "/requisites")).with(csrf()).cookie(owner.session())
                            .contentType("application/json").content(json("{'bin':'12345'}")))
                    .andExpect(status().isBadRequest());
            TestUser manager = addMember(owner, "manager@example.com", "MANAGER");
            mockMvc.perform(put(ws(owner, "/requisites")).with(csrf()).cookie(manager.session())
                            .contentType("application/json").content(json("{'legalName':'Mine'}")))
                    .andExpect(status().isForbidden());
            TestUser outsider = signUp("outsider@example.com");
            mockMvc.perform(get(ws(owner, "/orders/" + orderId + "/invoice")).cookie(outsider.session()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CustomerLink {

        private String createLink() throws Exception {
            String body = mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/share-link")).with(csrf())
                            .cookie(owner.session()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String url = JsonPath.read(body, "$.url");
            assertThat(url).startsWith("http://localhost:5173/p/");
            return url.substring(url.lastIndexOf('/') + 1);
        }

        @Test
        void customerSeesOnlyPublicFieldsWithoutAnAccount() throws Exception {
            pay(orderId, "100", TODAY);
            mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/notes")).with(csrf()).cookie(owner.session())
                    .contentType("application/json").content(json("{'body':'Secret internal note'}")));
            String token = createLink();

            String body = mockMvc.perform(get("/api/v1/public/orders/" + token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.orderNumber").value("ORD-000001"))
                    .andExpect(jsonPath("$.paidAmount").value(100))
                    .andExpect(jsonPath("$.payments[0].amount").value(100))
                    .andReturn().getResponse().getContentAsString();
            // The seller is the workspace (named after its owner by default); staff and client data are hidden
            assertThat((String) JsonPath.read(body, "$.sellerName")).isEqualTo("Test User");
            assertThat(body).doesNotContain("Secret internal note", "owner@example.com", client.toString(),
                    "assignedUser", "notes");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_share_links WHERE token_hash = ?",
                    Integer.class, token)).isZero(); // only the hash is stored
        }

        @Test
        void newLinkRevokesOldOneAndRevokedOrExpiredLinksAreGone() throws Exception {
            String first = createLink();
            String second = createLink();
            mockMvc.perform(get("/api/v1/public/orders/" + first)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/public/orders/" + second)).andExpect(status().isOk());

            jdbc.update("UPDATE order_share_links SET expires_at = now() - interval '1 minute'");
            mockMvc.perform(get("/api/v1/public/orders/" + second)).andExpect(status().isNotFound());

            String third = createLink();
            mockMvc.perform(delete(ws(owner, "/orders/" + orderId + "/share-link")).with(csrf()).cookie(owner.session()))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/v1/public/orders/" + third)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/public/orders/not-a-real-token")).andExpect(status().isNotFound());
        }

        @Test
        void employeesCannotCreateLinks() throws Exception {
            TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
            UUID assigned = idOf(createOrder(owner, client, employee.id()));
            mockMvc.perform(post(ws(owner, "/orders/" + assigned + "/share-link")).with(csrf())
                            .cookie(employee.session()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class CsvImport {

        private ResultActions upload(byte[] data, boolean dryRun) throws Exception {
            return mockMvc.perform(multipart(ws(owner, "/clients/import"))
                    .file(new MockMultipartFile("file", "clients.csv", "text/csv", data))
                    .param("dryRun", String.valueOf(dryRun)).with(csrf()).cookie(owner.session()));
        }

        @Test
        void previewThenImportOnlyValidNewRows() throws Exception {
            mockMvc.perform(put(ws(owner, "/clients/" + client)).with(csrf()).cookie(owner.session())
                    .contentType("application/json")
                    .content(json("{'name':'Kaspi Shop','phone':'+7 701 000 00 01'}")));
            String csv = """
                    Имя;Email;Телефон;Компания
                    Асель;asel@mail.kz;+7 702 111 22 33;"ТОО ""Алма""\"
                    ;nobody@mail.kz;;
                    Bad email;not-an-email;;
                    Дубль из базы;;8 701 000 00 01;
                    Дубль в файле;ASEL@mail.kz;;
                    """;
            byte[] data = csv.getBytes(StandardCharsets.UTF_8);

            upload(data, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(5))
                    .andExpect(jsonPath("$.valid").value(1))
                    .andExpect(jsonPath("$.invalid").value(2))
                    .andExpect(jsonPath("$.duplicates").value(2))
                    .andExpect(jsonPath("$.imported").value(0))
                    .andExpect(jsonPath("$.rows[1].errors[0]").value("name"))
                    .andExpect(jsonPath("$.rows[3].duplicateOf").value("Kaspi Shop"))
                    .andExpect(jsonPath("$.rows[4].duplicateOf").value("#file"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clients", Integer.class)).isEqualTo(1);

            upload(data, false).andExpect(jsonPath("$.imported").value(1));
            assertThat(jdbc.queryForObject("SELECT company FROM clients WHERE email = 'asel@mail.kz'", String.class))
                    .isEqualTo("ТОО \"Алма\"");
        }

        @Test
        void excelWindows1251FileIsReadCorrectly() throws Exception {
            byte[] cp1251 = "Имя,Телефон\r\nЖанна Әлиева,+7 705 123 45 67\r\n".replace('Ә', 'А')
                    .getBytes(Charset.forName("windows-1251"));
            upload(cp1251, false)
                    .andExpect(jsonPath("$.imported").value(1))
                    .andExpect(jsonPath("$.rows[0].name").value("Жанна Алиева"));
        }

        @Test
        void fileWithoutNameColumnOrForEmployeesIsRejected() throws Exception {
            upload("foo,bar\n1,2\n".getBytes(StandardCharsets.UTF_8), true)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IMPORT_NO_NAME_COLUMN"));
            TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
            mockMvc.perform(multipart(ws(owner, "/clients/import"))
                            .file(new MockMultipartFile("file", "c.csv", "text/csv", "name\nX\n".getBytes()))
                            .with(csrf()).cookie(employee.session()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class RevenueAndCalendar {

        @Test
        void revenueHasTwelveMonthsPerCurrency() throws Exception {
            pay(orderId, "300", TODAY);
            pay(orderId, "200", TODAY.minusMonths(2).withDayOfMonth(1));

            mockMvc.perform(get(ws(owner, "/dashboard/revenue")).cookie(owner.session()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.months.length()").value(12))
                    .andExpect(jsonPath("$.months[11]").value(YearMonth.from(TODAY).toString()))
                    .andExpect(jsonPath("$.series.KZT[11]").value(300))
                    .andExpect(jsonPath("$.series.KZT[9]").value(200))
                    .andExpect(jsonPath("$.series.KZT[10]").value(0));
        }

        @Test
        void calendarShowsDeadlinesInRangeForVisibleOrders() throws Exception {
            TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
            UUID mine = idOf(createOrder(owner, client, employee.id()));
            jdbc.update("UPDATE customer_orders SET deadline = ?", TODAY.plusDays(3));
            jdbc.update("UPDATE customer_orders SET deadline = ? WHERE id = ?", TODAY.minusDays(1), orderId);

            mockMvc.perform(get(ws(owner, "/calendar")).param("from", TODAY.minusDays(5).toString())
                            .param("to", TODAY.plusDays(10).toString()).cookie(owner.session()))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].overdue").value(true));
            mockMvc.perform(get(ws(owner, "/calendar")).param("from", TODAY.minusDays(5).toString())
                            .param("to", TODAY.plusDays(10).toString()).cookie(employee.session()))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(mine.toString()));
            mockMvc.perform(get(ws(owner, "/calendar")).param("from", TODAY.toString())
                            .param("to", TODAY.plusDays(100).toString()).cookie(owner.session()))
                    .andExpect(status().isBadRequest());
        }
    }
}
