package kz.clientflow.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByWorkspaceIdAndOrderIdOrderByPosition(UUID workspaceId, UUID orderId);

    boolean existsByWorkspaceIdAndOrderId(UUID workspaceId, UUID orderId);

    @Modifying
    @Query("delete from OrderItem i where i.workspaceId = :workspaceId and i.orderId = :orderId")
    void deleteAllOfOrder(UUID workspaceId, UUID orderId);
}
