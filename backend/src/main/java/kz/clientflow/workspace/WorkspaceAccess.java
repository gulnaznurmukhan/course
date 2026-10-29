package kz.clientflow.workspace;

import java.util.UUID;

/**
 * Result of a successful membership check: who is acting, in which workspace,
 * with which role. Services receive this instead of trusting IDs from the request.
 */
public record WorkspaceAccess(UUID workspaceId, UUID userId, WorkspaceRole role) {
}
