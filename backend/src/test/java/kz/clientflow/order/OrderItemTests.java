package kz.clientflow.order;

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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import kz.clientflow.IntegrationTest;

/** Price list and order line items: totals are calculated by the server. */
class OrderItemTests extends IntegrationTest {

    private TestUser owner;
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        orderId = idOf(createOrder(owner, createClient(owner, "Client"), null)); // KZT
    }

    private UUID service(String name, String price, String currency) throws Exception {
        return idOf(mockMvc.perform(post(ws(owner, "/services")).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'name':'%s','unit':'hour','price':%s,'currency':'%s'}", name, price, currency)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions putItems(TestUser user, long version, String itemsJson) throws Exception {
        return mockMvc.perform(put(ws(owner, "/orders/" + orderId + "/items")).with(csrf()).cookie(user.session())
                .contentType("application/json")
                .content(json("{'version':%d,'items':%s}", version, itemsJson)));
    }

    private BigDecimal total() throws Exception {
        String body = mockMvc.perform(get(ws(owner, "/orders/" + orderId)).cookie(owner.session()))
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.read(body, "$.totalAmount").toString());
    }

    @Test
    void catalogPriceIsUsedAndClientPriceIsIgnored() throws Exception {
        UUID design = service("Design", "15000", "KZT");

        putItems(owner, 0, json("[{'serviceId':'%s','quantity':2,'unitPrice':1,'name':'Cheap'}]", design))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Design"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(15000))
                .andExpect(jsonPath("$.items[0].lineTotal").value(30000))
                .andExpect(jsonPath("$.totalAmount").value(30000));
        assertThat(total()).isEqualByComparingTo("30000");
    }

    @Test
    void mixedItemsAreSummedWithHalfUpRounding() throws Exception {
        UUID design = service("Design", "333.33", "KZT");
        putItems(owner, 0, json("[{'serviceId':'%s','quantity':1.5},"
                        + "{'name':'Hosting','unit':'month','quantity':3,'unitPrice':1000}]", design))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineTotal").value(500.00)) // 499.995 -> 500.00
                .andExpect(jsonPath("$.items[1].lineTotal").value(3000))
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(total()).isEqualByComparingTo("3500.00");
    }

    @Test
    void priceListChangesDoNotAlterSavedItems() throws Exception {
        UUID design = service("Design", "100", "KZT");
        putItems(owner, 0, json("[{'serviceId':'%s','quantity':1}]", design)).andExpect(status().isOk());

        mockMvc.perform(put(ws(owner, "/services/" + design)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'name':'Design PRO','unit':'hour','price':999,'currency':'KZT'}")))
                .andExpect(status().isOk());

        mockMvc.perform(get(ws(owner, "/orders/" + orderId + "/items")).cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].name").value("Design"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100));
    }

    @Test
    void invalidItemsAreRejected() throws Exception {
        UUID usd = service("Consulting", "50", "USD");
        TestUser other = signUp("other@example.com");
        UUID foreign = idOf(mockMvc.perform(post(ws(other, "/services")).with(csrf()).cookie(other.session())
                        .contentType("application/json")
                        .content(json("{'name':'X','unit':'pcs','price':1,'currency':'KZT'}")))
                .andReturn().getResponse().getContentAsString());

        putItems(owner, 0, json("[{'serviceId':'%s','quantity':1}]", usd))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERVICE_CURRENCY_MISMATCH"));
        putItems(owner, 0, json("[{'serviceId':'%s','quantity':1}]", foreign))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        putItems(owner, 0, "[{\"name\":\"No price\",\"quantity\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITEM_INCOMPLETE"));
        putItems(owner, 0, "[{\"name\":\"Free\",\"quantity\":1,\"unitPrice\":0}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITEMS_TOTAL_ZERO"));
        putItems(owner, 0, "[{\"name\":\"X\",\"quantity\":0,\"unitPrice\":5}]")
                .andExpect(status().isBadRequest());
        assertThat(total()).isEqualByComparingTo("150000.50");
    }

    @Test
    void itemsCannotMakeTheTotalLowerThanPaid() throws Exception {
        mockMvc.perform(post(ws(owner, "/orders/" + orderId + "/payments")).with(csrf()).cookie(owner.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content(json("{'amount':5000,'paymentMethod':'CASH','paymentDate':'%s'}",
                                LocalDate.now(ZoneId.of("Asia/Almaty")))))
                .andExpect(status().isCreated());

        putItems(owner, 0, "[{\"name\":\"Small\",\"quantity\":1,\"unitPrice\":4999.99}]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOTAL_BELOW_PAID"));
    }

    @Test
    void totalIsManagedByItemsAndVersionIsChecked() throws Exception {
        putItems(owner, 0, "[{\"name\":\"Work\",\"quantity\":1,\"unitPrice\":700}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Stale version
        putItems(owner, 0, "[{\"name\":\"Work\",\"quantity\":2,\"unitPrice\":700}]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        // Manual total while items exist
        mockMvc.perform(put(ws(owner, "/orders/" + orderId)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Website','totalAmount':1,'currency':'KZT','version':1}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOTAL_MANAGED_BY_ITEMS"));
        // Editing other fields with the same total still works
        mockMvc.perform(put(ws(owner, "/orders/" + orderId)).with(csrf()).cookie(owner.session())
                        .contentType("application/json")
                        .content(json("{'title':'Renamed','totalAmount':700,'currency':'KZT','version':1}")))
                .andExpect(status().isOk());
    }

    @Test
    void employeesCanViewItemsButNotChangeThemOrThePriceList() throws Exception {
        TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        UUID assigned = idOf(createOrder(owner, createClient(owner, "C2"), employee.id()));

        mockMvc.perform(get(ws(owner, "/orders/" + assigned + "/items")).cookie(employee.session()))
                .andExpect(status().isOk());
        mockMvc.perform(put(ws(owner, "/orders/" + assigned + "/items")).with(csrf()).cookie(employee.session())
                        .contentType("application/json").content(json("{'version':0,'items':[]}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ws(owner, "/services")).with(csrf()).cookie(employee.session())
                        .contentType("application/json")
                        .content(json("{'name':'X','unit':'pcs','price':1,'currency':'KZT'}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ws(owner, "/services")).cookie(employee.session())).andExpect(status().isOk());
    }

    @Test
    void archivedServicesAreHiddenAndCannotBeAdded() throws Exception {
        UUID old = service("Old", "10", "KZT");
        mockMvc.perform(post(ws(owner, "/services/" + old + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(get(ws(owner, "/services")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        putItems(owner, 0, json("[{'serviceId':'%s','quantity':1}]", old))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SERVICE_ARCHIVED"));
    }
}
