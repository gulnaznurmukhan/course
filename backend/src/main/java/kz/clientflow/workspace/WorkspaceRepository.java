package kz.clientflow.workspace;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /**
     * Locks the workspace row until the transaction ends. Used to hand out
     * order numbers: two parallel requests can never get the same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = :id")
    Optional<Workspace> findForUpdate(UUID id);
}
