package kz.clientflow.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order life cycle. Allowed transitions:
 *
 *   NEW ---------> CONFIRMED ---------> IN_PROGRESS ---------> COMPLETED
 *    |                 |                     |
 *    +---------------> +-------------------> +---------------> CANCELLED
 *
 * IN_PROGRESS -> CANCELLED is an explicit addition to the examples in the
 * specification: work that has started can still be called off.
 * COMPLETED and CANCELLED are final.
 */
public enum OrderStatus {
    NEW, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case NEW -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    /** Work is not finished yet: the order counts as active and can become overdue. */
    public boolean isActive() {
        return this == NEW || this == CONFIRMED || this == IN_PROGRESS;
    }

    public static Set<OrderStatus> activeStatuses() {
        return EnumSet.of(NEW, CONFIRMED, IN_PROGRESS);
    }
}
