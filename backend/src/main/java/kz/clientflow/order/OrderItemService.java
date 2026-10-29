package kz.clientflow.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.catalog.CatalogItem;
import kz.clientflow.catalog.CatalogRepository;
import kz.clientflow.common.ApiException;
import kz.clientflow.notification.NotificationService;
import kz.clientflow.payment.PaymentRepository;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Line items of an order. When an order has items, its total is the sum of the
 * line totals and is always calculated here (never sent by the browser):
 *
 *   lineTotal = quantity * unitPrice   (rounded half-up to 2 decimals)
 *   total     = sum of lineTotal
 *
 * For a line taken from the price list the price is read from the catalog;
 * a price sent by the client for such a line is ignored.
 */
@Service
public class OrderItemService {

    private static final BigDecimal MAX_TOTAL = new BigDecimal("999999999999.99");
    private static final String DEFAULT_UNIT = "pcs";

    public record ItemRequest(
            UUID serviceId,
            @Size(max = 200) String name,
            @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
            BigDecimal quantity,
            @DecimalMin("0.00") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2)
            BigDecimal unitPrice) {
    }

    public record ReplaceItemsRequest(@NotNull Long version, @NotNull @Size(max = 100) List<@Valid ItemRequest> items) {
    }

    public record ItemResponse(UUID id, UUID serviceId, String name, String unit, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {

        static ItemResponse from(OrderItem i) {
            return new ItemResponse(i.getId(), i.getServiceId(), i.getName(), i.getUnit(), i.getQuantity(),
                    i.getUnitPrice(), i.getLineTotal());
        }
    }

    public record ItemsResponse(List<ItemResponse> items, BigDecimal totalAmount, String currency, long version) {
    }

    private final OrderService orderService;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final CatalogRepository catalog;
    private final PaymentRepository payments;
    private final ActivityService activity;
    private final NotificationService notifications;

    public OrderItemService(OrderService orderService, OrderRepository orders, OrderItemRepository items,
                            CatalogRepository catalog, PaymentRepository payments, ActivityService activity,
                            NotificationService notifications) {
        this.orderService = orderService;
        this.orders = orders;
        this.items = items;
        this.catalog = catalog;
        this.payments = payments;
        this.activity = activity;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public ItemsResponse list(WorkspaceAccess access, UUID orderId) {
        CustomerOrder order = orderService.requireVisible(access, orderId);
        return response(access, order);
    }

    /** Replaces all items of the order and recalculates the total. An empty list removes the items. */
    @Transactional
    public ItemsResponse replace(WorkspaceAccess access, UUID orderId, ReplaceItemsRequest request) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
        CustomerOrder order = orderService.requireVisible(access, orderId);
        if (order.getVersion() != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION");
        }
        if (order.isArchived() || !order.getStatus().isActive()) {
            throw ApiException.conflict("ORDER_NOT_EDITABLE");
        }
        // No payment may be recorded while the total is being recalculated
        orders.findForUpdate(orderId, access.workspaceId());

        List<OrderItem> newItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int position = 0;
        for (ItemRequest line : request.items()) {
            String name;
            String unit;
            BigDecimal unitPrice;
            if (line.serviceId() != null) {
                CatalogItem service = catalog.findByIdAndWorkspaceId(line.serviceId(), access.workspaceId())
                        .orElseThrow(() -> ApiException.badRequest("SERVICE_NOT_FOUND"));
                if (service.isArchived()) {
                    throw ApiException.badRequest("SERVICE_ARCHIVED");
                }
                if (!service.getCurrency().equals(order.getCurrency())) {
                    throw ApiException.badRequest("SERVICE_CURRENCY_MISMATCH");
                }
                name = service.getName();
                unit = service.getUnit();
                unitPrice = service.getPrice(); // the catalog price, not the one from the request
            } else {
                if (line.name() == null || line.name().isBlank() || line.unitPrice() == null) {
                    throw ApiException.badRequest("ITEM_INCOMPLETE");
                }
                name = line.name().trim();
                unit = line.unit() == null || line.unit().isBlank() ? DEFAULT_UNIT : line.unit().trim();
                unitPrice = line.unitPrice();
            }
            BigDecimal lineTotal = line.quantity().multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            total = total.add(lineTotal);
            newItems.add(new OrderItem(access.workspaceId(), orderId, line.serviceId(), position++, name, unit,
                    line.quantity(), unitPrice, lineTotal));
        }

        if (!newItems.isEmpty()) {
            if (total.signum() <= 0) {
                throw ApiException.badRequest("ITEMS_TOTAL_ZERO");
            }
            if (total.compareTo(MAX_TOTAL) > 0) {
                throw ApiException.badRequest("VALIDATION_FAILED");
            }
            if (total.compareTo(payments.sumValid(access.workspaceId(), orderId)) < 0) {
                throw ApiException.conflict("TOTAL_BELOW_PAID");
            }
            order.setTotalAmount(total);
        }

        items.deleteAllOfOrder(access.workspaceId(), orderId);
        items.saveAll(newItems);
        order.markChanged();
        orders.saveAndFlush(order);

        activity.record(access, "ORDER_ITEMS_UPDATED", ActivityLog.EntityType.ORDER, orderId,
                Map.of("count", newItems.size(), "total", order.getTotalAmount().toPlainString(),
                        "currency", order.getCurrency()));
        notifications.orderChanged(access, order, "UPDATED", Map.of("changedFields", List.of("items")));
        return response(access, order);
    }

    private ItemsResponse response(WorkspaceAccess access, CustomerOrder order) {
        return new ItemsResponse(
                items.findByWorkspaceIdAndOrderIdOrderByPosition(access.workspaceId(), order.getId()).stream()
                        .map(ItemResponse::from).toList(),
                order.getTotalAmount(), order.getCurrency(), order.getVersion());
    }
}
