package kz.clientflow.security;

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

/**
 * Users of one workspace must never see or change records of another workspace,
 * neither through the other workspace's URL nor by putting foreign IDs into their own (IDOR).
 */
class WorkspaceIsolationTests extends IntegrationTest {

    private TestUser alice;
    private TestUser bob;
    private UUID aliceClient;
    private UUID aliceOrder;

    @BeforeEach
    void setUp() throws Exception {
        alice = signUp("alice@example.com");
        bob = signUp("bob@example.com");
        aliceClient = createClient(alice, "Alice Client");
        aliceOrder = idOf(createOrder(alice, aliceClient, null));
    }

    @Test
    void outsiderCannotOpenAnotherWorkspace() throws Exception {
        mockMvc.perform(get(ws(alice, "")).cookie(bob.session())).andExpect(status().isNotFound());
        mockMvc.perform(get(ws(alice, "/clients")).cookie(bob.session())).andExpect(status().isNotFound());
        mockMvc.perform(get(ws(alice, "/orders")).cookie(bob.session())).andExpect(status().isNotFound());
        mockMvc.perform(get(ws(alice, "/members")).cookie(bob.session())).andExpect(status().isNotFound());
    }

    @Test
    void outsiderCannotReadOrChangeForeignRecordsThroughForeignWorkspace() throws Exception {
        mockMvc.perform(get(ws(alice, "/clients/" + aliceClient)).cookie(bob.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ws(alice, "/clients/" + aliceClient)).with(csrf()).cookie(bob.session())
                        .contentType("application/json").content(json("{'name':'Hacked'}")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ws(alice, "/orders/" + aliceOrder + "/archive")).with(csrf()).cookie(bob.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignIdsInsideOwnWorkspaceAreNotFound() throws Exception {
        // Bob uses his own workspace URL but Alice's record IDs
        mockMvc.perform(get(ws(bob, "/clients/" + aliceClient)).cookie(bob.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(bob, "/orders/" + aliceOrder)).cookie(bob.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ws(bob, "/orders/" + aliceOrder + "/notes")).with(csrf()).cookie(bob.session())
                        .contentType("application/json").content(json("{'body':'hi'}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotCreateOrderForForeignClientOrAssignForeignUser() throws Exception {
        mockMvc.perform(post(ws(bob, "/orders")).with(csrf()).cookie(bob.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'X','totalAmount':10,'currency':'KZT'}", aliceClient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));

        UUID bobClient = createClient(bob, "Bob Client");
        mockMvc.perform(post(ws(bob, "/orders")).with(csrf()).cookie(bob.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'X','totalAmount':10,'currency':'KZT',"
                                + "'assignedUserId':'%s'}", bobClient, alice.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_MEMBER"));
    }

    @Test
    void listsContainOnlyOwnWorkspaceRecords() throws Exception {
        createClient(bob, "Bob Client");

        mockMvc.perform(get(ws(bob, "/clients")).cookie(bob.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Bob Client"));
        mockMvc.perform(get(ws(bob, "/orders")).cookie(bob.session()))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get(ws(bob, "/clients/duplicates")).param("email", "").param("phone", "")
                        .cookie(bob.session()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void signedOutVisitorGets401() throws Exception {
        mockMvc.perform(get(ws(alice, "/clients"))).andExpect(status().isUnauthorized());
    }
}
