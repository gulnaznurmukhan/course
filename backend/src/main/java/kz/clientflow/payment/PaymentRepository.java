package kz.clientflow.payment;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"createdBy", "voidedBy"})
    List<Payment> findByWorkspaceIdAndOrderIdOrderByPaymentDateDescCreatedAtDesc(UUID workspaceId, UUID orderId);

    Optional<Payment> findByIdAndWorkspaceIdAndOrderId(UUID id, UUID workspaceId, UUID orderId);

    Optional<Payment> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, UUID idempotencyKey);

    /** Sum of valid (not voided) payments of one order. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.workspaceId = :workspaceId and p.orderId = :orderId and p.voidedAt is null""")
    BigDecimal sumValid(UUID workspaceId, UUID orderId);

    boolean existsByWorkspaceIdAndOrderIdAndVoidedAtIsNull(UUID workspaceId, UUID orderId);

    /** Paid amounts of many orders in one query (avoids one query per row in lists). */
    @Query("""
            select p.orderId as orderId, sum(p.amount) as paid from Payment p
            where p.workspaceId = :workspaceId and p.orderId in :orderIds and p.voidedAt is null
            group by p.orderId""")
    List<PaidAmount> sumValidByOrder(UUID workspaceId, Collection<UUID> orderIds);

    interface PaidAmount {
        UUID getOrderId();

        BigDecimal getPaid();
    }
}
