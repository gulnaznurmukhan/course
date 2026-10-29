package kz.clientflow.workspace;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.ApiException;

/**
 * Verifies on every request that the signed-in user belongs to the workspace
 * named in the URL. The role is always read from the database.
 *
 * Non-members get 404 (not 403), so the API does not even confirm that
 * a workspace with this id exists.
 */
@Service
public class WorkspaceAccessService {

    private final WorkspaceMembershipRepository memberships;

    public WorkspaceAccessService(WorkspaceMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public WorkspaceAccess requireMember(UUID workspaceId, UUID userId) {
        WorkspaceMembership membership = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(ApiException::notFound);
        return new WorkspaceAccess(workspaceId, userId, membership.getRole());
    }

    /** Member with permission to manage clients, orders and payments. */
    public WorkspaceAccess requireRecordManager(UUID workspaceId, UUID userId) {
        WorkspaceAccess access = requireMember(workspaceId, userId);
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
        return access;
    }

    public WorkspaceAccess requireOwner(UUID workspaceId, UUID userId) {
        WorkspaceAccess access = requireMember(workspaceId, userId);
        if (!access.role().canManageMembers()) {
            throw ApiException.forbidden();
        }
        return access;
    }
}
