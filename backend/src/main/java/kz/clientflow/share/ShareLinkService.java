package kz.clientflow.share;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.auth.RateLimiter;
import kz.clientflow.auth.token.TokenGenerator;
import kz.clientflow.common.ApiException;
import kz.clientflow.config.AppProperties;
import kz.clientflow.order.CustomerOrder;
import kz.clientflow.order.OrderItemRepository;
import kz.clientflow.order.OrderRepository;
import kz.clientflow.order.OrderService;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.PaymentRepository;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.workspace.Workspace;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceRepository;

/**
 * Customer-facing order links. A manager creates a link; anyone who has it can
 * see a short, read-only summary of that single order until it expires or is revoked.
 */
@Service
public class ShareLinkService {

    public static final Duration LIFETIME = Duration.ofDays(90);

    public record LinkStatus(boolean active, Instant createdAt, Instant expiresAt) {
    }

    /** The URL (with the raw token) is returned only once, right after creation. */
    public record CreatedLink(String url, Instant expiresAt) {
    }

    public record PublicItem(String name, String unit, BigDecimal quantity, BigDecimal lineTotal) {
    }

    public record PublicPayment(LocalDate date, BigDecimal amount) {
    }

    /** What the customer sees. No internal notes, staff names or contact data. */
    public record PublicOrder(String sellerName, String orderNumber, String title, OrderStatus status,
                              LocalDate deadline, String currency, BigDecimal totalAmount, BigDecimal paidAmount,
                              BigDecimal balance, PaymentState paymentState, List<PublicItem> items,
                              List<PublicPayment> payments, Instant updatedAt) {
    }

    private final ShareLinkRepository links;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final WorkspaceRepository workspaces;
    private final TokenGenerator tokens;
    private final RateLimiter rateLimiter;
    private final ActivityService activity;
    private final AppProperties properties;
    private final Clock clock;

    public ShareLinkService(ShareLinkRepository links, OrderService orderService, OrderRepository orders,
                            OrderItemRepository items, PaymentRepository payments, WorkspaceRepository workspaces,
                            TokenGenerator tokens, RateLimiter rateLimiter, ActivityService activity,
                            AppProperties properties, Clock clock) {
        this.links = links;
        this.orderService = orderService;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.workspaces = workspaces;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.activity = activity;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ managing links (OWNER / MANAGER)

    @Transactional(readOnly = true)
    public LinkStatus status(WorkspaceAccess access, UUID orderId) {
        requireManager(access);
        orderService.requireVisible(access, orderId);
        Instant now = Instant.now(clock);
        return links.findByWorkspaceIdAndOrderIdAndRevokedAtIsNull(access.workspaceId(), orderId).stream()
                .filter(l -> l.isActive(now))
                .max(Comparator.comparing(ShareLink::getCreatedAt))
                .map(l -> new LinkStatus(true, l.getCreatedAt(), l.getExpiresAt()))
                .orElse(new LinkStatus(false, null, null));
    }

    /** Creates a new link; any previous link of the order stops working. */
    @Transactional
    public CreatedLink create(WorkspaceAccess access, UUID orderId) {
        requireManager(access);
        orderService.requireVisible(access, orderId);
        Instant now = Instant.now(clock);
        revokeAll(access, orderId, now);

        TokenGenerator.GeneratedToken token = tokens.generate();
        ShareLink link = links.save(new ShareLink(access.workspaceId(), orderId, token.hash(), access.userId(),
                now.plus(LIFETIME)));
        activity.record(access, "SHARE_LINK_CREATED", ActivityLog.EntityType.ORDER, orderId, Map.of());
        String base = properties.publicUrl().replaceAll("/+$", "");
        return new CreatedLink(base + "/p/" + token.raw(), link.getExpiresAt());
    }

    @Transactional
    public void revoke(WorkspaceAccess access, UUID orderId) {
        requireManager(access);
        orderService.requireVisible(access, orderId);
        revokeAll(access, orderId, Instant.now(clock));
        activity.record(access, "SHARE_LINK_REVOKED", ActivityLog.EntityType.ORDER, orderId, Map.of());
    }

    private void revokeAll(WorkspaceAccess access, UUID orderId, Instant now) {
        links.findByWorkspaceIdAndOrderIdAndRevokedAtIsNull(access.workspaceId(), orderId)
                .forEach(l -> l.revoke(now));
    }

    // ------------------------------------------------------------------ public view (no account)

    /** Unknown, expired and revoked links all answer the same 404. */
    @Transactional(readOnly = true)
    public PublicOrder publicView(String rawToken, String clientIp) {
        rateLimiter.consume("public-order:ip:" + clientIp, 60, Duration.ofMinutes(1));
        Instant now = Instant.now(clock);
        ShareLink link = links.findByTokenHash(tokens.hash(rawToken))
                .filter(l -> l.isActive(now))
                .orElseThrow(ApiException::notFound);
        CustomerOrder order = orders.findByIdAndWorkspaceId(link.getOrderId(), link.getWorkspaceId())
                .orElseThrow(ApiException::notFound);
        Workspace workspace = workspaces.findById(link.getWorkspaceId()).orElseThrow(ApiException::notFound);

        BigDecimal paid = payments.sumValid(link.getWorkspaceId(), order.getId());
        String seller = workspace.getRequisites().legalName() != null
                ? workspace.getRequisites().legalName() : workspace.getName();
        return new PublicOrder(seller, order.getOrderNumber(), order.getTitle(), order.getStatus(),
                order.getDeadline(), order.getCurrency(), order.getTotalAmount(), paid,
                order.getTotalAmount().subtract(paid), PaymentState.of(paid, order.getTotalAmount()),
                items.findByWorkspaceIdAndOrderIdOrderByPosition(link.getWorkspaceId(), order.getId()).stream()
                        .map(i -> new PublicItem(i.getName(), i.getUnit(), i.getQuantity(), i.getLineTotal()))
                        .toList(),
                payments.findByWorkspaceIdAndOrderIdOrderByPaymentDateDescCreatedAtDesc(link.getWorkspaceId(),
                                order.getId()).stream()
                        .filter(p -> !p.isVoided())
                        .map(p -> new PublicPayment(p.getPaymentDate(), p.getAmount()))
                        .toList(),
                order.getUpdatedAt());
    }

    private static void requireManager(WorkspaceAccess access) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
    }
}
