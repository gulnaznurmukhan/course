package kz.clientflow.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kz.clientflow.activity.ActivityResponse;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.common.PageResponse;
import kz.clientflow.order.dto.OrderDtos.ArchiveFilter;
import kz.clientflow.order.dto.OrderDtos.BoardColumn;
import kz.clientflow.order.dto.OrderDtos.ChangeStatusRequest;
import kz.clientflow.order.dto.OrderDtos.CreateOrderRequest;
import kz.clientflow.order.dto.OrderDtos.NoteRequest;
import kz.clientflow.order.dto.OrderDtos.NoteResponse;
import kz.clientflow.order.dto.OrderDtos.OrderFilter;
import kz.clientflow.order.dto.OrderDtos.OrderResponse;
import kz.clientflow.order.dto.OrderDtos.SortField;
import kz.clientflow.order.dto.OrderDtos.UpdateOrderRequest;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderItemService itemService;
    private final InvoiceService invoiceService;
    private final WorkspaceAccessService accessService;

    public OrderController(OrderService orderService, OrderItemService itemService, InvoiceService invoiceService,
                           WorkspaceAccessService accessService) {
        this.orderService = orderService;
        this.itemService = itemService;
        this.invoiceService = invoiceService;
        this.accessService = accessService;
    }

    @GetMapping("/{orderId}/invoice")
    InvoiceService.InvoiceResponse invoice(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                           @AuthenticationPrincipal CurrentUser user) {
        return invoiceService.invoice(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @GetMapping("/{orderId}/items")
    OrderItemService.ItemsResponse items(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                         @AuthenticationPrincipal CurrentUser user) {
        return itemService.list(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @PutMapping("/{orderId}/items")
    OrderItemService.ItemsResponse replaceItems(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                                @AuthenticationPrincipal CurrentUser user,
                                                @Valid @RequestBody OrderItemService.ReplaceItemsRequest body) {
        return itemService.replace(accessService.requireMember(workspaceId, user.id()), orderId, body);
    }

    @GetMapping
    PageResponse<OrderResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                     @RequestParam(required = false) @Size(max = 100) String search,
                                     @RequestParam(defaultValue = "ACTIVE") ArchiveFilter archive,
                                     @RequestParam(required = false) OrderStatus status,
                                     @RequestParam(required = false) UUID clientId,
                                     @RequestParam(required = false) UUID assignedUserId,
                                     @RequestParam(required = false) Boolean overdue,
                                     @RequestParam(required = false) PaymentState paymentState,
                                     @RequestParam(defaultValue = "CREATED_AT") SortField sort,
                                     @RequestParam(defaultValue = "DESC") Sort.Direction direction,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        WorkspaceAccess access = accessService.requireMember(workspaceId, user.id());
        var filter = new OrderFilter(search, archive, status, clientId, assignedUserId, overdue, paymentState);
        return orderService.list(access, filter, page, size, sort, direction);
    }

    @GetMapping("/board")
    List<BoardColumn> board(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                            @RequestParam(required = false) @Size(max = 100) String search) {
        return orderService.board(accessService.requireMember(workspaceId, user.id()), search);
    }

    @GetMapping("/{orderId}")
    OrderResponse get(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                      @AuthenticationPrincipal CurrentUser user) {
        return orderService.get(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                         @Valid @RequestBody CreateOrderRequest body) {
        return orderService.create(accessService.requireMember(workspaceId, user.id()), body);
    }

    @PutMapping("/{orderId}")
    OrderResponse update(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                         @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateOrderRequest body) {
        return orderService.update(accessService.requireMember(workspaceId, user.id()), orderId, body);
    }

    @PostMapping("/{orderId}/status")
    OrderResponse changeStatus(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                               @AuthenticationPrincipal CurrentUser user,
                               @Valid @RequestBody ChangeStatusRequest body) {
        return orderService.changeStatus(accessService.requireMember(workspaceId, user.id()), orderId, body);
    }

    @PostMapping("/{orderId}/archive")
    OrderResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                          @AuthenticationPrincipal CurrentUser user) {
        return orderService.archive(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @PostMapping("/{orderId}/restore")
    OrderResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                          @AuthenticationPrincipal CurrentUser user) {
        return orderService.restore(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @GetMapping("/{orderId}/notes")
    List<NoteResponse> notes(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                             @AuthenticationPrincipal CurrentUser user) {
        return orderService.notes(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @PostMapping("/{orderId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    NoteResponse addNote(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                         @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NoteRequest body) {
        return orderService.addNote(accessService.requireMember(workspaceId, user.id()), orderId, body.body());
    }

    @GetMapping("/{orderId}/activity")
    PageResponse<ActivityResponse> activity(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                                            @AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return orderService.history(accessService.requireMember(workspaceId, user.id()), orderId, page, size);
    }
}
