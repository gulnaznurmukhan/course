package kz.clientflow.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityResponse;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.client.Client;
import kz.clientflow.client.ClientRepository;
import kz.clientflow.common.ApiException;
import kz.clientflow.common.PageResponse;
import kz.clientflow.order.dto.OrderDtos.ArchiveFilter;
import kz.clientflow.order.dto.OrderDtos.BoardColumn;
import kz.clientflow.order.dto.OrderDtos.ChangeStatusRequest;
import kz.clientflow.order.dto.OrderDtos.CreateOrderRequest;
import kz.clientflow.order.dto.OrderDtos.NoteResponse;
import kz.clientflow.order.dto.OrderDtos.OrderFilter;
import kz.clientflow.order.dto.OrderDtos.OrderResponse;
import kz.clientflow.order.dto.OrderDtos.Ref;
import kz.clientflow.order.dto.OrderDtos.SortField;
import kz.clientflow.order.dto.OrderDtos.UpdateOrderRequest;
import kz.clientflow.notification.NotificationService;
import kz.clientflow.payment.PaymentRepository;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.user.User;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.Workspace;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceMembershipRepository;
import kz.clientflow.workspace.WorkspaceRepository;

/**
 * Business rules for orders.
 *
 * - OWNER and MANAGER see and manage all orders of the workspace.
 * - EMPLOYEE sees only orders assigned to them (others answer 404) and may only
 *   move them forward: CONFIRMED -> IN_PROGRESS -> COMPLETED.
 * - Only active orders (NEW, CONFIRMED, IN_PROGRESS) can be edited.
 * - Only finished orders (COMPLETED, CANCELLED) can be archived.
 */
@Service
public class OrderService {

    private static final int BOARD_COLUMN_LIMIT = 50;

    private final OrderRepository orders;
    private final OrderNoteRepository notes;
    private final ClientRepository clients;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final OrderItemRepository items;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderNoteRepository notes, ClientRepository clients,
                        WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships,
                        UserRepository users, ActivityService activity, PaymentRepository payments,
                        NotificationService notifications, OrderItemRepository items, Clock clock) {
        this.orders = orders;
        this.notes = notes;
        this.clients = clients;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.users = users;
        this.activity = activity;
        this.payments = payments;
        this.notifications = notifications;
        this.items = items;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(WorkspaceAccess access, OrderFilter filter, int page, int size,
                                            SortField sort, Sort.Direction direction) {
        Sort.Order order = new Sort.Order(direction, sort.property(), Sort.NullHandling.NULLS_LAST);
        Pageable pageable = PageRequest.of(page, size, Sort.by(order, Sort.Order.desc("createdAt")));
        var result = orders.findAll(visibleOrders(access, filter), pageable);
        Map<UUID, BigDecimal> paid = paidAmounts(access, result.getContent());
        return PageResponse.of(result, o -> toResponse(o, access, paid.getOrDefault(o.getId(), BigDecimal.ZERO)));
    }

    /** Kanban board: non-archived orders grouped by status. */
    @Transactional(readOnly = true)
    public List<BoardColumn> board(WorkspaceAccess access, String search) {
        List<BoardColumn> columns = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            OrderFilter filter = new OrderFilter(search, ArchiveFilter.ACTIVE, status, null, null, null, null);
            Pageable pageable = PageRequest.of(0, BOARD_COLUMN_LIMIT,
                    Sort.by(new Sort.Order(Sort.Direction.ASC, "deadline", Sort.NullHandling.NULLS_LAST),
                            Sort.Order.desc("createdAt")));
            var result = orders.findAll(visibleOrders(access, filter), pageable);
            Map<UUID, BigDecimal> paid = paidAmounts(access, result.getContent());
            columns.add(new BoardColumn(status,
                    result.getContent().stream()
                            .map(o -> toResponse(o, access, paid.getOrDefault(o.getId(), BigDecimal.ZERO)))
                            .toList(),
                    result.getTotalElements()));
        }
        return columns;
    }

    @Transactional(readOnly = true)
    public OrderResponse get(WorkspaceAccess access, UUID orderId) {
        return toResponse(requireVisible(access, orderId), access);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> history(WorkspaceAccess access, UUID orderId, int page, int size) {
        requireVisible(access, orderId);
        return activity.forEntity(access.workspaceId(), ActivityLog.EntityType.ORDER, orderId, page, size);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(WorkspaceAccess access, UUID orderId) {
        requireVisible(access, orderId);
        return notes.findByWorkspaceIdAndOrderIdOrderByCreatedAtAsc(access.workspaceId(), orderId).stream()
                .map(OrderService::toNoteResponse)
                .toList();
    }

    // ------------------------------------------------------------------ changes

    @Transactional
    public OrderResponse create(WorkspaceAccess access, CreateOrderRequest request) {
        requireManager(access);
        Client client = clients.findByIdAndWorkspaceId(request.clientId(), access.workspaceId())
                .orElseThrow(() -> ApiException.badRequest("CLIENT_NOT_FOUND"));
        if (client.isArchived()) {
            throw ApiException.badRequest("CLIENT_ARCHIVED");
        }
        if (request.deadline() != null && request.deadline().isBefore(today())) {
            throw ApiException.badRequest("DEADLINE_IN_PAST");
        }

        // The workspace row stays locked until commit, so order numbers are unique and sequential
        Workspace workspace = workspaces.findForUpdate(access.workspaceId()).orElseThrow(ApiException::notFound);
        String number = "ORD-%06d".formatted(workspace.takeNextOrderNumber());

        CustomerOrder order = new CustomerOrder(access.workspaceId(), client, number, access.userId());
        order.setTitle(request.title().trim());
        order.setDescription(blankToNull(request.description()));
        order.setTotalAmount(request.totalAmount());
        order.setCurrency(request.currency());
        order.setDeadline(request.deadline());
        order.setAssignedUser(resolveAssignee(access, request.assignedUserId()));
        orders.save(order);

        activity.record(access, "ORDER_CREATED", ActivityLog.EntityType.ORDER, order.getId(),
                Map.of("orderNumber", number, "title", order.getTitle()));
        if (order.getAssignedUser() != null) {
            notifications.orderChanged(access, order, "ASSIGNED", Map.of());
        }
        return toResponse(order, access);
    }

    @Transactional
    public OrderResponse update(WorkspaceAccess access, UUID orderId, UpdateOrderRequest request) {
        requireManager(access);
        CustomerOrder order = requireVisible(access, orderId);
        requireVersion(order, request.version());
        if (!isEditable(order)) {
            throw ApiException.conflict("ORDER_NOT_EDITABLE");
        }
        boolean deadlineChanged = !Objects.equals(order.getDeadline(), request.deadline());
        if (deadlineChanged && request.deadline() != null && request.deadline().isBefore(today())) {
            throw ApiException.badRequest("DEADLINE_IN_PAST");
        }
        User assignee = resolveAssignee(access, request.assignedUserId());

        boolean totalChanged = order.getTotalAmount().compareTo(request.totalAmount()) != 0;
        boolean currencyChanged = !order.getCurrency().equals(request.currency());
        boolean hasItems = items.existsByWorkspaceIdAndOrderId(access.workspaceId(), orderId);
        if (hasItems && totalChanged) {
            throw ApiException.conflict("TOTAL_MANAGED_BY_ITEMS");
        }
        if (hasItems && currencyChanged) {
            throw ApiException.conflict("CURRENCY_LOCKED");
        }
        if (totalChanged || currencyChanged) {
            // Lock the row so no payment can be recorded while the total is being changed
            orders.findForUpdate(orderId, access.workspaceId());
            BigDecimal paid = payments.sumValid(access.workspaceId(), orderId);
            if (currencyChanged && paid.signum() > 0) {
                throw ApiException.conflict("CURRENCY_LOCKED");
            }
            if (request.totalAmount().compareTo(paid) < 0) {
                throw ApiException.conflict("TOTAL_BELOW_PAID");
            }
        }

        List<String> changed = new ArrayList<>();
        if (!order.getTitle().equals(request.title().trim())) changed.add("title");
        if (!Objects.equals(order.getDescription(), blankToNull(request.description()))) changed.add("description");
        if (order.getTotalAmount().compareTo(request.totalAmount()) != 0) changed.add("totalAmount");
        if (!order.getCurrency().equals(request.currency())) changed.add("currency");
        if (deadlineChanged) changed.add("deadline");
        if (!Objects.equals(idOf(order.getAssignedUser()), request.assignedUserId())) changed.add("assignedUser");

        order.setTitle(request.title().trim());
        order.setDescription(blankToNull(request.description()));
        order.setTotalAmount(request.totalAmount());
        order.setCurrency(request.currency());
        order.setDeadline(request.deadline());
        order.setAssignedUser(assignee);
        orders.saveAndFlush(order);

        if (!changed.isEmpty()) {
            activity.record(access, "ORDER_UPDATED", ActivityLog.EntityType.ORDER, order.getId(),
                    Map.of("changedFields", changed));
            if (changed.contains("assignedUser")) {
                notifications.orderChanged(access, order, "ASSIGNED", Map.of());
            } else {
                notifications.orderChanged(access, order, "UPDATED", Map.of("changedFields", changed));
            }
        }
        return toResponse(order, access);
    }

    @Transactional
    public OrderResponse changeStatus(WorkspaceAccess access, UUID orderId, ChangeStatusRequest request) {
        CustomerOrder order = requireVisible(access, orderId);
        requireVersion(order, request.version());
        if (order.isArchived()) {
            throw ApiException.conflict("ORDER_ARCHIVED");
        }
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION");
        }
        if (!allowedTransitions(order, access).contains(to)) {
            throw ApiException.forbidden();
        }
        // Money already received must be voided (refunded) before the order is cancelled
        if (to == OrderStatus.CANCELLED) {
            orders.findForUpdate(orderId, access.workspaceId());
            if (payments.existsByWorkspaceIdAndOrderIdAndVoidedAtIsNull(access.workspaceId(), orderId)) {
                throw ApiException.conflict("ORDER_HAS_PAYMENTS");
            }
        }

        order.setStatus(to);
        orders.saveAndFlush(order);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from", from.name());
        metadata.put("to", to.name());
        activity.record(access, "ORDER_STATUS_CHANGED", ActivityLog.EntityType.ORDER, order.getId(), metadata);
        notifications.orderChanged(access, order, "STATUS", metadata);
        return toResponse(order, access);
    }

    @Transactional
    public OrderResponse archive(WorkspaceAccess access, UUID orderId) {
        requireManager(access);
        CustomerOrder order = requireVisible(access, orderId);
        if (order.getStatus().isActive()) {
            throw ApiException.conflict("ORDER_NOT_FINISHED");
        }
        if (!order.isArchived()) {
            order.archive(Instant.now(clock));
            orders.saveAndFlush(order);
            activity.record(access, "ORDER_ARCHIVED", ActivityLog.EntityType.ORDER, order.getId(), Map.of());
        }
        return toResponse(order, access);
    }

    @Transactional
    public OrderResponse restore(WorkspaceAccess access, UUID orderId) {
        requireManager(access);
        CustomerOrder order = requireVisible(access, orderId);
        if (order.isArchived()) {
            order.restore();
            orders.saveAndFlush(order);
            activity.record(access, "ORDER_RESTORED", ActivityLog.EntityType.ORDER, order.getId(), Map.of());
        }
        return toResponse(order, access);
    }

    @Transactional
    public NoteResponse addNote(WorkspaceAccess access, UUID orderId, String body) {
        CustomerOrder order = requireVisible(access, orderId);
        OrderNote note = notes.save(new OrderNote(access.workspaceId(), order.getId(),
                users.getReferenceById(access.userId()), body.trim()));
        activity.record(access, "ORDER_NOTE_ADDED", ActivityLog.EntityType.ORDER, order.getId(), Map.of());
        return toNoteResponse(note);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Loads an order of this workspace that the user is allowed to see.
     * Orders of other workspaces, and for EMPLOYEE unassigned orders, answer 404.
     */
    public CustomerOrder requireVisible(WorkspaceAccess access, UUID orderId) {
        CustomerOrder order = orders.findByIdAndWorkspaceId(orderId, access.workspaceId())
                .orElseThrow(ApiException::notFound);
        if (!access.role().seesAllOrders() && !order.isAssignedTo(access.userId())) {
            throw ApiException.notFound();
        }
        return order;
    }

    private Specification<CustomerOrder> visibleOrders(
            WorkspaceAccess access, OrderFilter filter) {
        UUID onlyAssignedTo = access.role().seesAllOrders() ? null : access.userId();
        return OrderSpecifications.matching(access.workspaceId(), filter, onlyAssignedTo, today());
    }

    /** Status changes the current user may perform on this order. */
    private Set<OrderStatus> allowedTransitions(CustomerOrder order, WorkspaceAccess access) {
        if (order.isArchived()) {
            return EnumSet.noneOf(OrderStatus.class);
        }
        Set<OrderStatus> allowed = EnumSet.noneOf(OrderStatus.class);
        allowed.addAll(order.getStatus().allowedNext());
        if (!access.role().canManageRecords()) {
            // EMPLOYEE: only their own orders, only forward, never cancel
            allowed.retainAll(switch (order.getStatus()) {
                case CONFIRMED -> EnumSet.of(OrderStatus.IN_PROGRESS);
                case IN_PROGRESS -> EnumSet.of(OrderStatus.COMPLETED);
                default -> EnumSet.noneOf(OrderStatus.class);
            });
            if (!order.isAssignedTo(access.userId())) {
                allowed.clear();
            }
        }
        return allowed;
    }

    private boolean isEditable(CustomerOrder order) {
        return !order.isArchived() && order.getStatus().isActive();
    }

    private User resolveAssignee(WorkspaceAccess access, UUID assignedUserId) {
        if (assignedUserId == null) {
            return null;
        }
        if (!memberships.existsByWorkspaceIdAndUserId(access.workspaceId(), assignedUserId)) {
            throw ApiException.badRequest("ASSIGNEE_NOT_MEMBER");
        }
        return users.getReferenceById(assignedUserId);
    }

    private static void requireManager(WorkspaceAccess access) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
    }

    private static void requireVersion(CustomerOrder order, Long expectedVersion) {
        if (expectedVersion == null || order.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Paid amounts of the given orders, loaded with one query. */
    private Map<UUID, BigDecimal> paidAmounts(WorkspaceAccess access, List<CustomerOrder> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> result = new HashMap<>();
        payments.sumValidByOrder(access.workspaceId(), list.stream().map(CustomerOrder::getId).toList())
                .forEach(row -> result.put(row.getOrderId(), row.getPaid()));
        return result;
    }

    private OrderResponse toResponse(CustomerOrder o, WorkspaceAccess access) {
        return toResponse(o, access, payments.sumValid(access.workspaceId(), o.getId()));
    }

    private OrderResponse toResponse(CustomerOrder o, WorkspaceAccess access, BigDecimal paid) {
        User assignee = o.getAssignedUser();
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getTitle(), o.getDescription(),
                o.getTotalAmount(), o.getCurrency(), o.getStatus(), o.getDeadline(), o.isOverdue(today()),
                new Ref(o.getClient().getId(), o.getClient().getName()),
                assignee == null ? null : new Ref(assignee.getId(), assignee.getFullName()),
                o.isArchived(), o.getCreatedAt(), o.getUpdatedAt(), o.getVersion(),
                paid, o.getTotalAmount().subtract(paid), PaymentState.of(paid, o.getTotalAmount()),
                List.copyOf(allowedTransitions(o, access)),
                access.role().canManageRecords() && isEditable(o));
    }

    private static NoteResponse toNoteResponse(OrderNote note) {
        User author = note.getAuthor();
        return new NoteResponse(note.getId(), note.getBody(),
                author == null ? null : new Ref(author.getId(), author.getFullName()), note.getCreatedAt());
    }

    private static UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
