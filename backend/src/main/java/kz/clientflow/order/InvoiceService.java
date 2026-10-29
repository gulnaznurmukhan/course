package kz.clientflow.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.client.Client;
import kz.clientflow.common.ApiException;
import kz.clientflow.payment.PaymentRepository;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.workspace.Requisites;
import kz.clientflow.workspace.Workspace;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceRepository;

/**
 * Builds the data of an invoice ("счёт на оплату") for an order. The frontend only
 * lays it out for printing / saving as PDF; every amount comes from here.
 */
@Service
public class InvoiceService {

    public record Party(String name, String company, String address, String phone, String email) {
    }

    public record Line(int number, String name, String unit, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal lineTotal) {
    }

    public record InvoiceResponse(String number, LocalDate issueDate, LocalDate dueDate, String workspaceName,
                                  Requisites seller, Party buyer, String currency, List<Line> lines,
                                  BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal balance,
                                  PaymentState paymentState, OrderStatus orderStatus) {
    }

    private final OrderService orderService;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final WorkspaceRepository workspaces;
    private final Clock clock;

    public InvoiceService(OrderService orderService, OrderItemRepository items, PaymentRepository payments,
                          WorkspaceRepository workspaces, Clock clock) {
        this.orderService = orderService;
        this.items = items;
        this.payments = payments;
        this.workspaces = workspaces;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse invoice(WorkspaceAccess access, UUID orderId) {
        CustomerOrder order = orderService.requireVisible(access, orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE");
        }
        Workspace workspace = workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound);
        Client client = order.getClient();

        List<OrderItem> orderItems = items.findByWorkspaceIdAndOrderIdOrderByPosition(access.workspaceId(), orderId);
        List<Line> lines = orderItems.isEmpty()
                // No line items: the whole order is one line
                ? List.of(new Line(1, order.getTitle(), "pcs", BigDecimal.ONE, order.getTotalAmount(),
                        order.getTotalAmount()))
                : IntStream.range(0, orderItems.size())
                        .mapToObj(i -> {
                            OrderItem item = orderItems.get(i);
                            return new Line(i + 1, item.getName(), item.getUnit(), item.getQuantity(),
                                    item.getUnitPrice(), item.getLineTotal());
                        })
                        .toList();

        BigDecimal paid = payments.sumValid(access.workspaceId(), orderId);
        return new InvoiceResponse(order.getOrderNumber(), LocalDate.now(clock), order.getDeadline(),
                workspace.getName(), workspace.getRequisites(),
                new Party(client.getName(), client.getCompany(), client.getAddress(), client.getPhone(),
                        client.getEmail()),
                order.getCurrency(), lines, order.getTotalAmount(), paid, order.getTotalAmount().subtract(paid),
                PaymentState.of(paid, order.getTotalAmount()), order.getStatus());
    }
}
