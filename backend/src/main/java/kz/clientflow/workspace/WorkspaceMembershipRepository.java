package kz.clientflow.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    @EntityGraph(attributePaths = "workspace")
    List<WorkspaceMembership> findByUserIdOrderByCreatedAtAsc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<WorkspaceMembership> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
