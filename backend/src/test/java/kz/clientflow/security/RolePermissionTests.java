package kz.clientflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kz.clientflow.IntegrationTest;

/** OWNER / MANAGER / EMPLOYEE permissions are enforced by the backend. */
class RolePermissionTests extends IntegrationTest {

    private TestUser owner;
    private TestUser manager;
    private TestUser employee;
    private UUID client;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
        manager = addMember(owner, "manager@example.com", "MANAGER");
        employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        client = createClient(owner, "Client");
    }

    @Test
    void employeeCanViewClientsButNotChangeThem() throws Exception {
        mockMvc.perform(get(ws(owner, "/clients/" + client)).cookie(employee.session()))
                .andExpect(status().isOk());
        mockMvc.perform(post(ws(owner, "/clients")).with(csrf()).cookie(employee.session())
                        .contentType("application/json").content(json("{'name':'New'}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ws(owner, "/clients/" + client)).with(csrf()).cookie(employee.session())
                        .contentType("application/json").content(json("{'name':'Renamed'}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ws(owner, "/clients/" + client + "/archive")).with(csrf()).cookie(employee.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeSeesOnlyAssignedOrders() throws Exception {
        UUID assigned = idOf(createOrder(owner, client, employee.id()));
        UUID other = idOf(createOrder(owner, client, manager.id()));

        mockMvc.perform(get(ws(owner, "/orders")).cookie(employee.session()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(assigned.toString()));
        mockMvc.perform(get(ws(owner, "/orders/" + other)).cookie(employee.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/orders/" + other + "/notes")).cookie(employee.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/orders")).cookie(manager.session()))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void employeeCannotCreateOrEditOrders() throws Exception {
        String order = createOrder(owner, client, employee.id());

        mockMvc.perform(post(ws(owner, "/orders")).with(csrf()).cookie(employee.session())
                        .contentType("application/json")
                        .content(json("{'clientId':'%s','title':'X','totalAmount':10,'currency':'KZT'}", client)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ws(owner, "/orders/" + idOf(order))).with(csrf()).cookie(employee.session())
                        .contentType("application/json")
                        .content(json("{'title':'X','totalAmount':1,'currency':'KZT','version':%d}", versionOf(order))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeMovesOwnOrderForwardButCannotCancel() throws Exception {
        String order = createOrder(owner, client, employee.id());
        UUID id = idOf(order);

        // NEW -> CONFIRMED is a manager decision
        mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(employee.session())
                        .contentType("application/json").content(json("{'status':'CONFIRMED','version':0}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(manager.session())
                        .contentType("application/json").content(json("{'status':'CONFIRMED','version':0}")))
                .andExpect(status().isOk());

        String started = mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf())
                        .cookie(employee.session()).contentType("application/json")
                        .content(json("{'status':'IN_PROGRESS','version':1}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions[0]").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post(ws(owner, "/orders/" + id + "/status")).with(csrf()).cookie(employee.session())
                        .contentType("application/json")
                        .content(json("{'status':'CANCELLED','version':%d}", versionOf(started))))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerManagesMembers() throws Exception {
        mockMvc.perform(post(ws(owner, "/members")).with(csrf()).cookie(manager.session())
                        .contentType("application/json").content(json("{'email':'x@example.com','role':'EMPLOYEE'}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(ws(owner, "/members/" + employee.id())).with(csrf()).cookie(manager.session())
                        .contentType("application/json").content(json("{'role':'MANAGER'}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ws(owner, "/members/" + employee.id())).with(csrf()).cookie(manager.session()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(ws(owner, "")).with(csrf()).cookie(manager.session())
                        .contentType("application/json").content(json("{'name':'Mine now'}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void workspaceActivityIsForManagersOnly() throws Exception {
        mockMvc.perform(get(ws(owner, "/activity")).cookie(manager.session())).andExpect(status().isOk());
        mockMvc.perform(get(ws(owner, "/activity")).cookie(employee.session())).andExpect(status().isForbidden());
    }

    @Test
    void roleCannotBeEscalatedToOwner() throws Exception {
        mockMvc.perform(patch(ws(owner, "/members/" + manager.id())).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'role':'OWNER'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    }

    @Test
    void roleChangeTakesEffectImmediately() throws Exception {
        mockMvc.perform(patch(ws(owner, "/members/" + manager.id())).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'role':'EMPLOYEE'}")))
                .andExpect(status().isNoContent());

        // Same session, but the role is read from the database on every request
        mockMvc.perform(post(ws(owner, "/clients")).with(csrf()).cookie(manager.session())
                        .contentType("application/json").content(json("{'name':'New'}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void removedMemberLosesAccessAndOrdersBecomeUnassigned() throws Exception {
        UUID order = idOf(createOrder(owner, client, employee.id()));

        mockMvc.perform(delete(ws(owner, "/members/" + employee.id())).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ws(owner, "/orders")).cookie(employee.session())).andExpect(status().isNotFound());
        mockMvc.perform(get(ws(owner, "/orders/" + order)).cookie(owner.session()))
                .andExpect(jsonPath("$.assignedUser").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE action = 'MEMBER_REMOVED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void ownerCannotRemoveOrDemoteThemselves() throws Exception {
        mockMvc.perform(delete(ws(owner, "/members/" + owner.id())).with(csrf()).cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_SELF"));
        mockMvc.perform(patch(ws(owner, "/members/" + owner.id())).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'role':'EMPLOYEE'}")))
                .andExpect(status().isBadRequest());
    }
}
