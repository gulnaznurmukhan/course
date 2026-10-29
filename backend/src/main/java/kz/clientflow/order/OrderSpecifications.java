package kz.clientflow.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import kz.clientflow.common.SearchText;
import kz.clientflow.order.dto.OrderDtos.ArchiveFilter;
import kz.clientflow.order.dto.OrderDtos.OrderFilter;
import kz.clientflow.payment.Payment;

/** Builds the WHERE clause for order lists. Always restricted to one workspace. */
final class OrderSpecifications {

    private OrderSpecifications() {
    }

    /**
     * @param onlyAssignedTo when not null, only orders assigned to this user (EMPLOYEE role)
     */
    static Specification<CustomerOrder> matching(UUID workspaceId, OrderFilter filter, UUID onlyAssignedTo,
                                                 LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("workspaceId"), workspaceId));

            if (onlyAssignedTo != null) {
                where.add(cb.equal(root.get("assignedUser").get("id"), onlyAssignedTo));
            }

            ArchiveFilter archive = filter.archive() == null ? ArchiveFilter.ACTIVE : filter.archive();
            if (archive == ArchiveFilter.ACTIVE) {
                where.add(cb.isNull(root.get("archivedAt")));
            } else if (archive == ArchiveFilter.ARCHIVED) {
                where.add(cb.isNotNull(root.get("archivedAt")));
            }

            if (filter.status() != null) {
                where.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.clientId() != null) {
                where.add(cb.equal(root.get("client").get("id"), filter.clientId()));
            }
            if (filter.assignedUserId() != null) {
                where.add(cb.equal(root.get("assignedUser").get("id"), filter.assignedUserId()));
            }
            if (Boolean.TRUE.equals(filter.overdue())) {
                where.add(cb.lessThan(root.get("deadline"), today));
                where.add(root.get("status").in(OrderStatus.activeStatuses()));
            }
            if (filter.paymentState() != null) {
                // paid = SUM(amount) of valid payments of this order (0 when there are none)
                Subquery<BigDecimal> sum = query.subquery(BigDecimal.class);
                Root<Payment> payment = sum.from(Payment.class);
                sum.select(cb.coalesce(cb.sum(payment.get("amount")), BigDecimal.ZERO))
                        .where(cb.equal(payment.get("orderId"), root.get("id")),
                                cb.isNull(payment.get("voidedAt")));
                where.add(switch (filter.paymentState()) {
                    case UNPAID -> cb.equal(sum, BigDecimal.ZERO);
                    case PARTIALLY_PAID -> cb.and(cb.greaterThan(sum, BigDecimal.ZERO),
                            cb.lessThan(sum, root.get("totalAmount")));
                    case PAID -> cb.greaterThanOrEqualTo(sum, root.get("totalAmount"));
                });
            }
            if (!SearchText.isBlank(filter.search())) {
                String pattern = SearchText.containsPattern(filter.search());
                var client = root.join("client", JoinType.INNER);
                where.add(cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("title")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(client.get("name")), pattern, SearchText.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
