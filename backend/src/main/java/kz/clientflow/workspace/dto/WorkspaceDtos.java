package kz.clientflow.workspace.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.clientflow.workspace.WorkspaceRole;

/** Request and response bodies of the workspace API. */
public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record WorkspaceResponse(UUID id, String name, WorkspaceRole myRole) {
    }

    public record RenameRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull WorkspaceRole role) {
    }

    public record ChangeRoleRequest(@NotNull WorkspaceRole role) {
    }

    public record MemberResponse(UUID userId, String fullName, String email, WorkspaceRole role, Instant joinedAt) {
    }

    public record InvitationResponse(UUID id, String email, WorkspaceRole role, Instant createdAt) {
    }

    /** Pending invitations are only included for the OWNER. */
    public record MembersResponse(List<MemberResponse> members, List<InvitationResponse> invitations) {
    }
}
