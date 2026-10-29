package kz.clientflow.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kz.clientflow.activity.ActivityResponse;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.Payment;
import kz.clientflow.payment.PaymentState;

/** Response bodies of the dashboard and report APIs. Money is always grouped by currency. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record CurrencyAmount(String currency, BigDecimal amount, long count) {
    }

    public record UpcomingDeadline(UUID id, String orderNumber, String title, String clientName,
                                   LocalDate deadline, OrderStatus status) {
    }

    public record DashboardResponse(
            LocalDate from,
            LocalDate to,
            /** True for EMPLOYEE: all order and payment numbers cover only their assigned orders. */
            boolean onlyAssignedOrders,
            long totalClients,
            long activeOrders,
            long completedOrders,
            long overdueOrders,
            Map<OrderStatus, Long> ordersByStatus,
            List<CurrencyAmount> paymentsReceived,
            List<CurrencyAmount> outstandingBalances,
            List<UpcomingDeadline> upcomingDeadlines,
            /** Null for EMPLOYEE (the workspace activity log is for managers). */
            List<ActivityResponse> recentActivity) {
    }

    public record OrderReportRow(UUID id, String orderNumber, LocalDate createdDate, String clientName, String title,
                                 OrderStatus status, String currency, BigDecimal totalAmount, BigDecimal paidAmount,
                                 BigDecimal balance, PaymentState paymentState, LocalDate deadline, String assignee,
                                 boolean archived) {
    }

    /** Balance excludes cancelled orders (nothing is owed for them). */
    public record OrderSummary(String currency, long count, BigDecimal totalAmount, BigDecimal paidAmount,
                               BigDecimal balance) {
    }

    public record OrderReportResponse(List<OrderSummary> summary, List<OrderReportRow> items, int page, int size,
                                      long totalItems, int totalPages) {
    }

    public record PaymentReportRow(UUID id, LocalDate paymentDate, UUID orderId, String orderNumber,
                                   String clientName, BigDecimal amount, String currency, Payment.Method method,
                                   String reference, String recordedBy, boolean voided, String voidReason) {
    }

    /** Summary counts only valid payments; voided ones are listed but not added up. */
    public record PaymentReportResponse(List<CurrencyAmount> summary, List<PaymentReportRow> items, int page,
                                        int size, long totalItems, int totalPages) {
    }
}
