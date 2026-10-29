package kz.clientflow.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import kz.clientflow.IntegrationTest;

class MembershipTests extends IntegrationTest {

    private TestUser owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = signUp("owner@example.com");
    }

    private void invite(String email, String role) throws Exception {
        mockMvc.perform(post(ws(owner, "/members")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'email':'%s','role':'%s'}", email, role)))
                .andExpect(status().isAccepted());
    }

    @Test
    void invitingVerifiedUserAddsThemImmediately() throws Exception {
        TestUser member = signUp("member@example.com");
        invite("Member@Example.com", "MANAGER");

        mockMvc.perform(get(ws(owner, "/members")).cookie(owner.session()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1].email").value("member@example.com"))
                .andExpect(jsonPath("$.members[1].role").value("MANAGER"))
                .andExpect(jsonPath("$.invitations.length()").value(0));
        mockMvc.perform(get("/api/v1/users/me").cookie(member.session()))
                .andExpect(jsonPath("$.workspaces.length()").value(2));
        // The email is written in the inviter's language (English in this test)
        assertThat(sentEmails().getLast().getSubject()).isEqualTo("ClientFlow: workspace invitation");
        assertThat(sentEmails().getLast().getTo()).containsExactly("member@example.com");
    }

    @Test
    void invitationForUnknownEmailWaitsUntilEmailIsConfirmed() throws Exception {
        invite("newcomer@example.com", "EMPLOYEE");

        mockMvc.perform(get(ws(owner, "/members")).cookie(owner.session()))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.invitations[0].email").value("newcomer@example.com"));

        TestUser newcomer = signUp("newcomer@example.com");

        String me = mockMvc.perform(get("/api/v1/users/me").cookie(newcomer.session()))
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(me, "$.workspaces.length()")).isEqualTo(2);
        mockMvc.perform(get(ws(owner, "/clients")).cookie(newcomer.session())).andExpect(status().isOk());
        mockMvc.perform(get(ws(owner, "/members")).cookie(owner.session()))
                .andExpect(jsonPath("$.invitations.length()").value(0));
    }

    @Test
    void unverifiedAccountDoesNotGetAccessFromInvitation() throws Exception {
        register("pending@example.com");
        invite("pending@example.com", "EMPLOYEE");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM workspace_memberships m JOIN users u ON u.id = m.user_id
                WHERE u.email = 'pending@example.com' AND m.workspace_id = ?""", Integer.class,
                owner.workspaceId())).isZero();
    }

    @Test
    void pendingInvitationCanBeCancelled() throws Exception {
        invite("newcomer@example.com", "EMPLOYEE");
        String id = JsonPath.read(mockMvc.perform(get(ws(owner, "/members")).cookie(owner.session()))
                .andReturn().getResponse().getContentAsString(), "$.invitations[0].id");

        mockMvc.perform(delete(ws(owner, "/invitations/" + id)).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());

        TestUser newcomer = signUp("newcomer@example.com");
        mockMvc.perform(get(ws(owner, "/clients")).cookie(newcomer.session())).andExpect(status().isNotFound());
    }

    @Test
    void ownerCannotBeInvitedAsOwner() throws Exception {
        mockMvc.perform(post(ws(owner, "/members")).with(csrf()).cookie(owner.session())
                        .contentType("application/json").content(json("{'email':'x@example.com','role':'OWNER'}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membersSeeColleaguesButNotPendingInvitations() throws Exception {
        TestUser employee = addMember(owner, "employee@example.com", "EMPLOYEE");
        invite("newcomer@example.com", "EMPLOYEE");

        mockMvc.perform(get(ws(owner, "/members")).cookie(employee.session()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.invitations.length()").value(0));
    }
}
