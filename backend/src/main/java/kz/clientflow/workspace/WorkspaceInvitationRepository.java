package kz.clientflow.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    List<WorkspaceInvitation> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmail(UUID workspaceId, String email);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @EntityGraph(attributePaths = "workspace")
    List<WorkspaceInvitation> findByEmail(String email);
}
