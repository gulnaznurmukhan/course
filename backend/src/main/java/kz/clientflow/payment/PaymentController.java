package kz.clientflow.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.payment.dto.PaymentDtos.OrderPaymentsResponse;
import kz.clientflow.payment.dto.PaymentDtos.PaymentResponse;
import kz.clientflow.payment.dto.PaymentDtos.RecordPaymentRequest;
import kz.clientflow.payment.dto.PaymentDtos.VoidPaymentRequest;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final WorkspaceAccessService accessService;

    public PaymentController(PaymentService paymentService, WorkspaceAccessService accessService) {
        this.paymentService = paymentService;
        this.accessService = accessService;
    }

    @GetMapping
    OrderPaymentsResponse list(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                               @AuthenticationPrincipal CurrentUser user) {
        return paymentService.list(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    /**
     * Records a payment. The client must send a new random Idempotency-Key (UUID) for
     * every payment it submits and reuse it when retrying the same submission.
     * 201 = created, 200 = this key was already processed (nothing new created).
     */
    @PostMapping
    ResponseEntity<PaymentResponse> record(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                           @AuthenticationPrincipal CurrentUser user,
                                           @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                           @Valid @RequestBody RecordPaymentRequest body) {
        var result = paymentService.record(accessService.requireMember(workspaceId, user.id()), orderId,
                idempotencyKey, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{paymentId}/void")
    PaymentResponse voidPayment(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                @PathVariable UUID paymentId, @AuthenticationPrincipal CurrentUser user,
                                @Valid @RequestBody VoidPaymentRequest body) {
        return paymentService.voidPayment(accessService.requireMember(workspaceId, user.id()), orderId, paymentId,
                body.reason());
    }
}
