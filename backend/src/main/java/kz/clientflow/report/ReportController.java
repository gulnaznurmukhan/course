package kz.clientflow.report;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.Payment;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.report.dto.ReportDtos.DashboardResponse;
import kz.clientflow.report.dto.ReportDtos.OrderReportResponse;
import kz.clientflow.report.dto.ReportDtos.PaymentReportResponse;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class ReportController {

    private final ReportService reportService;
    private final WorkspaceAccessService accessService;

    public ReportController(ReportService reportService, WorkspaceAccessService accessService) {
        this.reportService = reportService;
        this.accessService = accessService;
    }

    @GetMapping("/dashboard")
    DashboardResponse dashboard(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.dashboard(accessService.requireMember(workspaceId, user.id()), from, to);
    }

    @GetMapping("/dashboard/revenue")
    ReportService.RevenueResponse revenue(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        return reportService.revenue(accessService.requireMember(workspaceId, user.id()));
    }

    @GetMapping("/calendar")
    List<ReportRepository.CalendarEntry> calendar(
            @PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.calendar(accessService.requireMember(workspaceId, user.id()), from, to);
    }

    @GetMapping("/reports/orders")
    OrderReportResponse orders(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) UUID clientId,
                               @RequestParam(required = false) OrderStatus status,
                               @RequestParam(required = false) PaymentState paymentState,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        WorkspaceAccess access = accessService.requireRecordManager(workspaceId, user.id());
        return reportService.orderReport(
                reportService.orderFilter(access, from, to, clientId, status, paymentState), page, size);
    }

    @GetMapping("/reports/orders.csv")
    void exportOrders(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) UUID clientId,
                      @RequestParam(required = false) OrderStatus status,
                      @RequestParam(required = false) PaymentState paymentState,
                      HttpServletResponse response) throws IOException {
        WorkspaceAccess access = accessService.requireRecordManager(workspaceId, user.id());
        var filter = reportService.orderFilter(access, from, to, clientId, status, paymentState);
        prepareCsv(response, "orders_%s_%s.csv".formatted(filter.from(), filter.to()));
        reportService.exportOrders(filter, response.getOutputStream());
    }

    @GetMapping("/reports/payments")
    PaymentReportResponse payments(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) UUID clientId,
                                   @RequestParam(required = false) Payment.Method method,
                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        WorkspaceAccess access = accessService.requireRecordManager(workspaceId, user.id());
        return reportService.paymentReport(reportService.paymentFilter(access, from, to, clientId, method), page, size);
    }

    @GetMapping("/reports/payments.csv")
    void exportPayments(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) UUID clientId,
                        @RequestParam(required = false) Payment.Method method,
                        HttpServletResponse response) throws IOException {
        WorkspaceAccess access = accessService.requireRecordManager(workspaceId, user.id());
        var filter = reportService.paymentFilter(access, from, to, clientId, method);
        prepareCsv(response, "payments_%s_%s.csv".formatted(filter.from(), filter.to()));
        reportService.exportPayments(filter, response.getOutputStream());
    }

    /** Exports contain business data: they are downloaded, never cached. */
    private static void prepareCsv(HttpServletResponse response, String fileName) {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
