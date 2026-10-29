package kz.clientflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.clientflow.order.dto.OrderDtos.Ref;
import kz.clientflow.payment.Payment;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.user.User;

/** Request and response bodies of the payments API. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record RecordPaymentRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2)
            BigDecimal amount,
            @NotNull Payment.Method paymentMethod,
            @NotNull LocalDate paymentDate,
            @Size(max = 100) String reference) {
    }

    public record VoidPaymentRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record PaymentResponse(
            UUID id,
            BigDecimal amount,
            Payment.Method paymentMethod,
            LocalDate paymentDate,
            String reference,
            Ref createdBy,
            Instant createdAt,
            boolean voided,
            Instant voidedAt,
            Ref voidedBy,
            String voidReason) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getAmount(), p.getPaymentMethod(), p.getPaymentDate(),
                    p.getReference(), ref(p.getCreatedBy()), p.getCreatedAt(), p.isVoided(), p.getVoidedAt(),
                    ref(p.getVoidedBy()), p.getVoidReason());
        }

        private static Ref ref(User user) {
            return user == null ? null : new Ref(user.getId(), user.getFullName());
        }
    }

    /** All payments of an order with the calculated totals. */
    public record OrderPaymentsResponse(
            String currency,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            PaymentState paymentState,
            boolean canRecord,
            List<PaymentResponse> payments) {
    }
}
