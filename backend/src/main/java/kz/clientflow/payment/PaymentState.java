package kz.clientflow.payment;

import java.math.BigDecimal;

/**
 * UNPAID         - nothing paid yet
 * PARTIALLY_PAID - something paid, but less than the order total
 * PAID           - paid amount equals the order total
 */
public enum PaymentState {
    UNPAID, PARTIALLY_PAID, PAID;

    public static PaymentState of(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) {
            return UNPAID;
        }
        return paid.compareTo(total) >= 0 ? PAID : PARTIALLY_PAID;
    }
}
