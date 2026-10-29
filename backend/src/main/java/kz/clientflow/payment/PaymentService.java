package kz.clientflow.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.common.ApiException;
import kz.clientflow.order.CustomerOrder;
import kz.clientflow.order.OrderRepository;
import kz.clientflow.order.OrderService;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.dto.PaymentDtos.OrderPaymentsResponse;
import kz.clientflow.payment.dto.PaymentDtos.PaymentResponse;
import kz.clientflow.payment.dto.PaymentDtos.RecordPaymentRequest;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Financial rules for payments.
 *
 *   paidAmount = sum of payments that are not voided
 *   balance    = order total - paidAmount   (never stored, always calculated)
 *
 * Every change locks the order row (SELECT ... FOR UPDATE) for the rest of the
 * transaction. Two parallel payments for the same order are therefore processed
 * one after another, and the second one sees the first when checking the limit.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final UserRepository users;
    private final ActivityService activity;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderService orderService,
                          UserRepository users, ActivityService activity, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.users = users;
        this.activity = activity;
        this.clock = clock;
    }

    /** Result of recording: created=false means the same request was already processed. */
    public record RecordResult(PaymentResponse payment, boolean created) {
    }

    @Transactional(readOnly = true)
    public OrderPaymentsResponse list(WorkspaceAccess access, UUID orderId) {
        // Same visibility as the order itself (EMPLOYEE: only assigned orders)
        CustomerOrder order = orderService.requireVisible(access, orderId);
        BigDecimal paid = payments.sumValid(access.workspaceId(), orderId);
        BigDecimal total = order.getTotalAmount();
        boolean canRecord = access.role().canManageRecords() && acceptsPayments(order);
        return new OrderPaymentsResponse(order.getCurrency(), total, paid, total.subtract(paid),
                PaymentState.of(paid, total), canRecord,
                payments.findByWorkspaceIdAndOrderIdOrderByPaymentDateDescCreatedAtDesc(access.workspaceId(), orderId)
                        .stream().map(PaymentResponse::from).toList());
    }

    @Transactional
    public RecordResult record(WorkspaceAccess access, UUID orderId, UUID idempotencyKey,
                               RecordPaymentRequest request) {
        requireManager(access);
        CustomerOrder order = orders.findForUpdate(orderId, access.workspaceId()).orElseThrow(ApiException::notFound);

        // Duplicate submission (double click, network retry): return the payment created the first time
        var existing = payments.findByWorkspaceIdAndIdempotencyKey(access.workspaceId(), idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getOrderId().equals(orderId)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED");
            }
            return new RecordResult(PaymentResponse.from(existing.get()), false);
        }

        if (!acceptsPayments(order)) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE");
        }
        if (request.paymentDate().isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("PAYMENT_DATE_IN_FUTURE");
        }
        BigDecimal paid = payments.sumValid(access.workspaceId(), orderId);
        if (paid.add(request.amount()).compareTo(order.getTotalAmount()) > 0) {
            throw ApiException.conflict("OVERPAYMENT");
        }

        Payment payment = payments.save(new Payment(access.workspaceId(), orderId, request.amount(),
                request.paymentMethod(), request.paymentDate(), blankToNull(request.reference()), idempotencyKey,
                users.getReferenceById(access.userId())));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("paymentId", payment.getId().toString());
        metadata.put("amount", payment.getAmount().toPlainString());
        metadata.put("currency", order.getCurrency());
        metadata.put("method", payment.getPaymentMethod().name());
        activity.record(access, "PAYMENT_RECORDED", ActivityLog.EntityType.ORDER, orderId, metadata);
        return new RecordResult(PaymentResponse.from(payment), true);
    }

    /** Controlled correction: the payment stays in the history but no longer counts. */
    @Transactional
    public PaymentResponse voidPayment(WorkspaceAccess access, UUID orderId, UUID paymentId, String reason) {
        requireManager(access);
        CustomerOrder order = orders.findForUpdate(orderId, access.workspaceId()).orElseThrow(ApiException::notFound);
        Payment payment = payments.findByIdAndWorkspaceIdAndOrderId(paymentId, access.workspaceId(), orderId)
                .orElseThrow(ApiException::notFound);
        if (payment.isVoided()) {
            throw ApiException.conflict("PAYMENT_ALREADY_VOIDED");
        }
        payment.voidPayment(users.getReferenceById(access.userId()), reason.trim(), Instant.now(clock));
        payments.flush();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("paymentId", payment.getId().toString());
        metadata.put("amount", payment.getAmount().toPlainString());
        metadata.put("currency", order.getCurrency());
        metadata.put("reason", payment.getVoidReason());
        activity.record(access, "PAYMENT_VOIDED", ActivityLog.EntityType.ORDER, orderId, metadata);
        return PaymentResponse.from(payment);
    }

    /** Payments are accepted for orders that are not cancelled and not archived. */
    private static boolean acceptsPayments(CustomerOrder order) {
        return !order.isArchived() && order.getStatus() != OrderStatus.CANCELLED;
    }

    private static void requireManager(WorkspaceAccess access) {
        if (!access.role().canManageRecords()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
