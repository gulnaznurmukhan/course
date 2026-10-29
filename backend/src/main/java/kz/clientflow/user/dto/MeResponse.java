package kz.clientflow.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import kz.clientflow.user.Theme;
import kz.clientflow.workspace.WorkspaceRole;

/** Profile of the signed-in user and the workspaces they belong to. */
public record MeResponse(
        UUID id,
        String fullName,
        String email,
        String preferredLanguage,
        Theme theme,
        NotificationSettings notifications,
        /** Null when there is no avatar; otherwise its version for cache busting. */
        Instant avatarUpdatedAt,
        List<WorkspaceSummary> workspaces) {

    public record WorkspaceSummary(UUID id, String name, WorkspaceRole role) {
    }

    public record NotificationSettings(boolean deadlines, boolean overdue, boolean orderChanges, boolean payments) {
    }
}
