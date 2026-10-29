package kz.clientflow.activity;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.PageResponse;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Writes and reads the activity history.
 * record() joins the caller's transaction, so the log entry is saved
 * together with the change it describes (or not at all).
 */
@Service
public class ActivityService {

    private final ActivityLogRepository logs;
    private final UserRepository users;

    public ActivityService(ActivityLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @Transactional
    public void record(WorkspaceAccess access, String action, ActivityLog.EntityType entityType, UUID entityId,
                       Map<String, Object> metadata) {
        logs.save(new ActivityLog(access.workspaceId(), users.getReferenceById(access.userId()), action,
                entityType, entityId, metadata));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forEntity(UUID workspaceId, ActivityLog.EntityType entityType,
                                                    UUID entityId, int page, int size) {
        return PageResponse.of(logs.findByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                workspaceId, entityType, entityId, PageRequest.of(page, size)), ActivityResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forWorkspace(UUID workspaceId, int page, int size) {
        return PageResponse.of(logs.findByWorkspaceIdOrderByCreatedAtDescIdDesc(workspaceId,
                PageRequest.of(page, size)), ActivityResponse::from);
    }
}
