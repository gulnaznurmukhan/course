package kz.clientflow.order;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** Every lookup includes the workspace id, so records of other workspaces are never returned. */
public interface OrderRepository extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    @EntityGraph(attributePaths = {"client", "assignedUser"})
    Optional<CustomerOrder> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Row lock for financial operations (payments) on this order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.workspaceId = :workspaceId")
    Optional<CustomerOrder> findForUpdate(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndClientId(UUID workspaceId, UUID clientId);

    long countByWorkspaceIdAndClientIdAndStatusIn(UUID workspaceId, UUID clientId,
                                                   Collection<OrderStatus> statuses);

    /** Used when a member leaves the workspace: their orders become unassigned. */
    @Modifying
    @Query("""
            update CustomerOrder o set o.assignedUser = null
            where o.workspaceId = :workspaceId and o.assignedUser.id = :userId""")
    int unassignAll(UUID workspaceId, UUID userId);
}
