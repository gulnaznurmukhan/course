package kz.clientflow.report;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.activity.ActivityService;
import kz.clientflow.common.ApiException;
import kz.clientflow.config.AppProperties;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.Payment;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.report.ReportRepository.OrderReportFilter;
import kz.clientflow.report.ReportRepository.PaymentReportFilter;
import kz.clientflow.report.dto.ReportDtos.DashboardResponse;
import kz.clientflow.report.dto.ReportDtos.OrderReportResponse;
import kz.clientflow.report.dto.ReportDtos.PaymentReportResponse;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Dashboard statistics and reports. All numbers come from database queries
 * restricted to the workspace; nothing is hard-coded.
 *
 * Reports and exports are for OWNER and MANAGER (checked by the controller).
 * The dashboard is available to every member; for EMPLOYEE it only covers
 * the orders assigned to them.
 */
@Service
public class ReportService {

    /** Upper limit for one CSV export, to protect the server. */
    public static final int MAX_EXPORT_ROWS = 50_000;
    private static final int MAX_RANGE_DAYS = 366 * 5;

    private final ReportRepository reports;
    private final ActivityService activity;
    private final AppProperties properties;
    private final Clock clock;

    public ReportService(ReportRepository reports, ActivityService activity, AppProperties properties, Clock clock) {
        this.reports = reports;
        this.activity = activity;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(WorkspaceAccess access, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate periodFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate periodTo = to != null ? to : today;
        validateRange(periodFrom, periodTo);

        UUID onlyAssigned = access.role().seesAllOrders() ? null : access.userId();
        var byStatus = reports.countOrdersByStatus(access.workspaceId(), onlyAssigned);
        long active = byStatus.get(OrderStatus.NEW) + byStatus.get(OrderStatus.CONFIRMED)
                + byStatus.get(OrderStatus.IN_PROGRESS);

        return new DashboardResponse(periodFrom, periodTo, onlyAssigned != null,
                reports.countActiveClients(access.workspaceId()),
                active,
                byStatus.get(OrderStatus.COMPLETED),
                reports.countOverdue(access.workspaceId(), onlyAssigned, today),
                byStatus,
                reports.paymentsReceived(access.workspaceId(), onlyAssigned, periodFrom, periodTo),
                reports.outstandingBalances(access.workspaceId(), onlyAssigned),
                reports.upcomingDeadlines(access.workspaceId(), onlyAssigned, today, today.plusDays(7), 5),
                access.role().canManageRecords()
                        ? activity.forWorkspace(access.workspaceId(), 0, 10).items()
                        : null);
    }

    public record RevenueResponse(List<String> months, Map<String, List<BigDecimal>> series) {
    }

    /**
     * Payments received per month for the last 12 months (including the current one),
     * one series per currency. Months without payments are 0.
     */
    @Transactional(readOnly = true)
    public RevenueResponse revenue(WorkspaceAccess access) {
        YearMonth current = YearMonth.now(clock);
        List<String> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            months.add(current.minusMonths(i).toString());
        }
        UUID onlyAssigned = access.role().seesAllOrders() ? null : access.userId();
        Map<String, List<BigDecimal>> series = new TreeMap<>();
        for (var row : reports.monthlyPayments(access.workspaceId(), onlyAssigned,
                current.minusMonths(11).atDay(1))) {
            List<BigDecimal> values = series.computeIfAbsent(row.currency(),
                    c -> new ArrayList<>(Collections.nCopies(months.size(), BigDecimal.ZERO)));
            values.set(months.indexOf(row.month()), row.amount());
        }
        return new RevenueResponse(months, series);
    }

    @Transactional(readOnly = true)
    public List<ReportRepository.CalendarEntry> calendar(WorkspaceAccess access, LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 62) {
            throw ApiException.badRequest("INVALID_DATE_RANGE");
        }
        UUID onlyAssigned = access.role().seesAllOrders() ? null : access.userId();
        return reports.calendar(access.workspaceId(), onlyAssigned, from, to, LocalDate.now(clock));
    }

    // ------------------------------------------------------------------ order report

    public OrderReportFilter orderFilter(WorkspaceAccess access, LocalDate from, LocalDate to, UUID clientId,
                                         OrderStatus status, PaymentState paymentState) {
        LocalDate[] range = defaultRange(from, to);
        return new OrderReportFilter(access.workspaceId(), range[0], range[1], properties.timezone(), clientId, status,
                paymentState);
    }

    @Transactional(readOnly = true)
    public OrderReportResponse orderReport(OrderReportFilter filter, int page, int size) {
        long total = reports.countOrderRows(filter);
        return new OrderReportResponse(reports.orderSummary(filter), reports.orderRows(filter, size, page * size),
                page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public void exportOrders(OrderReportFilter filter, OutputStream output) throws IOException {
        try (CsvWriter csv = new CsvWriter(output)) {
            csv.row(List.of("order_number", "created_date", "client", "title", "status", "currency", "total_amount",
                    "paid_amount", "balance", "payment_state", "deadline", "assignee", "archived"));
            reports.streamOrderRows(filter, MAX_EXPORT_ROWS, r -> csv.row(Arrays.asList(
                    r.orderNumber(), r.createdDate(), r.clientName(), r.title(), r.status(), r.currency(),
                    r.totalAmount().toPlainString(), r.paidAmount().toPlainString(), r.balance().toPlainString(),
                    r.paymentState(), r.deadline(), r.assignee(), r.archived())));
        }
    }

    // ------------------------------------------------------------------ payment report

    public PaymentReportFilter paymentFilter(WorkspaceAccess access, LocalDate from, LocalDate to, UUID clientId,
                                             Payment.Method method) {
        LocalDate[] range = defaultRange(from, to);
        return new PaymentReportFilter(access.workspaceId(), range[0], range[1], clientId, method);
    }

    @Transactional(readOnly = true)
    public PaymentReportResponse paymentReport(PaymentReportFilter filter, int page, int size) {
        long total = reports.countPaymentRows(filter);
        return new PaymentReportResponse(reports.paymentSummary(filter), reports.paymentRows(filter, size, page * size),
                page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public void exportPayments(PaymentReportFilter filter, OutputStream output) throws IOException {
        try (CsvWriter csv = new CsvWriter(output)) {
            csv.row(List.of("payment_date", "order_number", "client", "amount", "currency", "method", "reference",
                    "recorded_by", "voided", "void_reason"));
            reports.streamPaymentRows(filter, MAX_EXPORT_ROWS, r -> csv.row(Arrays.asList(
                    r.paymentDate(), r.orderNumber(), r.clientName(), r.amount().toPlainString(), r.currency(),
                    r.method(), r.reference(), r.recordedBy(), r.voided(), r.voidReason())));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Default period: from the first day of the current month until today. */
    private LocalDate[] defaultRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        validateRange(start, end);
        return new LocalDate[] {start, end};
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("INVALID_DATE_RANGE");
        }
    }
}
