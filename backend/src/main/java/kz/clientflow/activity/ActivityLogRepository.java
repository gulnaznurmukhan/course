package kz.clientflow.activity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
            UUID workspaceId, ActivityLog.EntityType entityType, UUID entityId, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findByWorkspaceIdOrderByCreatedAtDescIdDesc(UUID workspaceId, Pageable pageable);
}
