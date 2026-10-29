package kz.clientflow.client;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kz.clientflow.IntegrationTest;

class ClientTests extends IntegrationTest {

    private TestUser owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
    }

    private UUID create(String jsonBody) throws Exception {
        String body = mockMvc.perform(post(ws(owner, "/clients")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json(jsonBody)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    @Test
    void createStoresNormalizedFieldsAndIgnoresForbiddenOnes() throws Exception {
        UUID other = UUID.randomUUID();
        UUID id = create("{'name':'  Asel  ','email':'Asel@Mail.KZ','phone':'+7 701 111 22 33','company':'',"
                + "'workspaceId':'" + other + "','archivedAt':'2020-01-01T00:00:00Z'}");

        mockMvc.perform(get(ws(owner, "/clients/" + id)).cookie(owner.session()))
                .andExpect(jsonPath("$.client.name").value("Asel"))
                .andExpect(jsonPath("$.client.email").value("asel@mail.kz"))
                .andExpect(jsonPath("$.client.company").doesNotExist())
                .andExpect(jsonPath("$.client.archived").value(false))
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mockMvc.perform(post(ws(owner, "/clients")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'name':'','email':'nope','phone':'abc'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void searchSortAndPaginate() throws Exception {
        create("{'name':'Charlie','company':'Kaspi'}");
        create("{'name':'alpha'}");
        create("{'name':'Bravo','email':'bravo@kaspi.kz'}");

        mockMvc.perform(get(ws(owner, "/clients")).param("size", "2").cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].name").value("alpha"))
                .andExpect(jsonPath("$.items[1].name").value("Bravo"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(ws(owner, "/clients")).param("search", "KASPI").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(get(ws(owner, "/clients")).param("sort", "NAME").param("direction", "DESC")
                        .param("size", "1").param("page", "1").cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].name").value("Bravo"));
    }

    @Test
    void pageSizeAndSortFieldAreLimited() throws Exception {
        mockMvc.perform(get(ws(owner, "/clients")).param("size", "1000").cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ws(owner, "/clients")).param("sort", "passwordHash").cookie(owner.session()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ws(owner, "/clients")).param("page", "-1").cookie(owner.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archiveAndRestore() throws Exception {
        UUID id = create("{'name':'Asel'}");

        mockMvc.perform(post(ws(owner, "/clients/" + id + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get(ws(owner, "/clients")).cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(owner, "/clients")).param("status", "ARCHIVED").cookie(owner.session()))
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(post(ws(owner, "/clients/" + id + "/restore")).with(csrf()).cookie(owner.session()))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void clientWithActiveOrdersCannotBeArchived() throws Exception {
        UUID id = create("{'name':'Asel'}");
        createOrder(owner, id, null);

        mockMvc.perform(post(ws(owner, "/clients/" + id + "/archive")).with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_HAS_ACTIVE_ORDERS"));
    }

    @Test
    void duplicateContactsAreFoundByEmailAndPhoneDigits() throws Exception {
        UUID id = create("{'name':'Asel','email':'asel@mail.kz','phone':'+7 (701) 111-22-33'}");

        mockMvc.perform(get(ws(owner, "/clients/duplicates")).param("email", "ASEL@mail.kz")
                        .cookie(owner.session()))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        mockMvc.perform(get(ws(owner, "/clients/duplicates")).param("phone", "+77011112233")
                        .cookie(owner.session()))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        // In Kazakhstan 8 and +7 prefixes mean the same number
        mockMvc.perform(get(ws(owner, "/clients/duplicates")).param("phone", "8 701 111 22 33")
                        .cookie(owner.session()))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        // Editing the same client is not a duplicate of itself
        mockMvc.perform(get(ws(owner, "/clients/duplicates")).param("email", "asel@mail.kz")
                        .param("excludeId", id.toString()).cookie(owner.session()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateIsLoggedInActivity() throws Exception {
        UUID id = create("{'name':'Asel'}");
        mockMvc.perform(put(ws(owner, "/clients/" + id)).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'name':'Asel B.','notes':'VIP'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("VIP"));

        mockMvc.perform(get(ws(owner, "/activity")).cookie(owner.session()))
                .andExpect(jsonPath("$.items[0].action").value("CLIENT_UPDATED"))
                .andExpect(jsonPath("$.items[1].action").value("CLIENT_CREATED"));
    }
}
