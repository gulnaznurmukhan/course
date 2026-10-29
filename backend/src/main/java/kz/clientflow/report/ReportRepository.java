package kz.clientflow.report;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.Payment;
import kz.clientflow.payment.PaymentState;
import kz.clientflow.report.dto.ReportDtos.CurrencyAmount;
import kz.clientflow.report.dto.ReportDtos.OrderReportRow;
import kz.clientflow.report.dto.ReportDtos.OrderSummary;
import kz.clientflow.report.dto.ReportDtos.PaymentReportRow;
import kz.clientflow.report.dto.ReportDtos.UpcomingDeadline;

/**
 * Aggregate queries for the dashboard and reports, written in plain SQL because
 * they are sums and groupings that the database calculates far better than Java.
 *
 * Every query is restricted to one workspace. Optional filters are added as
 * fixed SQL fragments; all values are bound as parameters (no string concatenation
 * of user input).
 */
@Repository
public class ReportRepository {

    /** Paid amount per order, counting only payments that were not voided. */
    private static final String PAID_PER_ORDER = """
            LEFT JOIN (SELECT order_id, sum(amount) AS paid FROM payments
                       WHERE workspace_id = :ws AND voided_at IS NULL GROUP BY order_id) pay
                   ON pay.order_id = o.id""";

    private static final String ACTIVE_STATUSES = "('NEW', 'CONFIRMED', 'IN_PROGRESS')";

    private final NamedParameterJdbcTemplate jdbc;

    public ReportRepository(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        // Stream large exports row by row instead of loading everything into memory
        template.setFetchSize(500);
        this.jdbc = new NamedParameterJdbcTemplate(template);
    }

    // ------------------------------------------------------------------ dashboard

    /** "AND o.assigned_user_id = :assignee" for EMPLOYEE, nothing for managers. */
    private static String scope(MapSqlParameterSource params, UUID onlyAssignedTo) {
        if (onlyAssignedTo == null) {
            return "";
        }
        params.addValue("assignee", onlyAssignedTo);
        return " AND o.assigned_user_id = :assignee";
    }

    public long countActiveClients(UUID workspaceId) {
        return jdbc.queryForObject("SELECT count(*) FROM clients WHERE workspace_id = :ws AND archived_at IS NULL",
                new MapSqlParameterSource("ws", workspaceId), Long.class);
    }

    /** Number of orders per status (all orders, including archived ones). */
    public Map<OrderStatus, Long> countOrdersByStatus(UUID workspaceId, UUID onlyAssignedTo) {
        var params = new MapSqlParameterSource("ws", workspaceId);
        String sql = "SELECT o.status, count(*) AS n FROM customer_orders o WHERE o.workspace_id = :ws"
                + scope(params, onlyAssignedTo) + " GROUP BY o.status";
        var result = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            result.put(status, 0L);
        }
        jdbc.query(sql, params, (ResultSet rs) -> {
            result.put(OrderStatus.valueOf(rs.getString("status")), rs.getLong("n"));
        });
        return result;
    }

    public long countOverdue(UUID workspaceId, UUID onlyAssignedTo, LocalDate today) {
        var params = new MapSqlParameterSource("ws", workspaceId).addValue("today", today);
        String sql = "SELECT count(*) FROM customer_orders o WHERE o.workspace_id = :ws AND o.deadline < :today"
                + " AND o.status IN " + ACTIVE_STATUSES + scope(params, onlyAssignedTo);
        return jdbc.queryForObject(sql, params, Long.class);
    }

    /** Valid payments received in the period, per currency. */
    public List<CurrencyAmount> paymentsReceived(UUID workspaceId, UUID onlyAssignedTo, LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource("ws", workspaceId).addValue("from", from).addValue("to", to);
        String sql = "SELECT o.currency, sum(p.amount) AS amount, count(*) AS n"
                + " FROM payments p JOIN customer_orders o ON o.id = p.order_id"
                + " WHERE p.workspace_id = :ws AND p.voided_at IS NULL"
                + " AND p.payment_date BETWEEN :from AND :to" + scope(params, onlyAssignedTo)
                + " GROUP BY o.currency ORDER BY o.currency";
        return jdbc.query(sql, params, (rs, i) ->
                new CurrencyAmount(rs.getString("currency").trim(), rs.getBigDecimal("amount"), rs.getLong("n")));
    }

    /** Unpaid balances of non-cancelled orders, per currency. */
    public List<CurrencyAmount> outstandingBalances(UUID workspaceId, UUID onlyAssignedTo) {
        var params = new MapSqlParameterSource("ws", workspaceId);
        String sql = "SELECT o.currency, sum(o.total_amount - coalesce(pay.paid, 0)) AS amount, count(*) AS n"
                + " FROM customer_orders o " + PAID_PER_ORDER
                + " WHERE o.workspace_id = :ws AND o.status <> 'CANCELLED'"
                + " AND o.total_amount > coalesce(pay.paid, 0)" + scope(params, onlyAssignedTo)
                + " GROUP BY o.currency ORDER BY o.currency";
        return jdbc.query(sql, params, (rs, i) ->
                new CurrencyAmount(rs.getString("currency").trim(), rs.getBigDecimal("amount"), rs.getLong("n")));
    }

    public List<UpcomingDeadline> upcomingDeadlines(UUID workspaceId, UUID onlyAssignedTo, LocalDate today,
                                                    LocalDate until, int limit) {
        var params = new MapSqlParameterSource("ws", workspaceId)
                .addValue("today", today).addValue("until", until).addValue("limit", limit);
        String sql = "SELECT o.id, o.order_number, o.title, o.deadline, o.status, c.name AS client_name"
                + " FROM customer_orders o JOIN clients c ON c.id = o.client_id"
                + " WHERE o.workspace_id = :ws AND o.archived_at IS NULL"
                + " AND o.status IN " + ACTIVE_STATUSES
                + " AND o.deadline BETWEEN :today AND :until" + scope(params, onlyAssignedTo)
                + " ORDER BY o.deadline, o.created_at LIMIT :limit";
        return jdbc.query(sql, params, (rs, i) -> new UpcomingDeadline(rs.getObject("id", UUID.class),
                rs.getString("order_number"), rs.getString("title"), rs.getString("client_name"),
                rs.getObject("deadline", LocalDate.class), OrderStatus.valueOf(rs.getString("status"))));
    }

    /** Valid payments per currency and month since the given date. */
    public List<MonthlyAmount> monthlyPayments(UUID workspaceId, UUID onlyAssignedTo, LocalDate from) {
        var params = new MapSqlParameterSource("ws", workspaceId).addValue("from", from);
        String sql = "SELECT o.currency, to_char(date_trunc('month', p.payment_date), 'YYYY-MM') AS month,"
                + " sum(p.amount) AS amount"
                + " FROM payments p JOIN customer_orders o ON o.id = p.order_id"
                + " WHERE p.workspace_id = :ws AND p.voided_at IS NULL AND p.payment_date >= :from"
                + scope(params, onlyAssignedTo)
                + " GROUP BY o.currency, month ORDER BY month";
        return jdbc.query(sql, params, (rs, i) -> new MonthlyAmount(rs.getString("currency").trim(),
                rs.getString("month"), rs.getBigDecimal("amount")));
    }

    public record MonthlyAmount(String currency, String month, BigDecimal amount) {
    }

    /** Orders with a deadline in the period (not archived), for the calendar. */
    public List<CalendarEntry> calendar(UUID workspaceId, UUID onlyAssignedTo, LocalDate from, LocalDate to,
                                        LocalDate today) {
        var params = new MapSqlParameterSource("ws", workspaceId)
                .addValue("from", from).addValue("to", to).addValue("today", today);
        String sql = "SELECT o.id, o.order_number, o.title, o.deadline, o.status, c.name AS client_name,"
                + " (o.deadline < :today AND o.status IN " + ACTIVE_STATUSES + ") AS overdue"
                + " FROM customer_orders o JOIN clients c ON c.id = o.client_id"
                + " WHERE o.workspace_id = :ws AND o.archived_at IS NULL"
                + " AND o.deadline BETWEEN :from AND :to" + scope(params, onlyAssignedTo)
                + " ORDER BY o.deadline, o.order_number LIMIT 1000";
        return jdbc.query(sql, params, (rs, i) -> new CalendarEntry(rs.getObject("id", UUID.class),
                rs.getString("order_number"), rs.getString("title"), rs.getString("client_name"),
                rs.getObject("deadline", LocalDate.class), OrderStatus.valueOf(rs.getString("status")),
                rs.getBoolean("overdue")));
    }

    public record CalendarEntry(UUID id, String orderNumber, String title, String clientName, LocalDate deadline,
                                OrderStatus status, boolean overdue) {
    }

    // ------------------------------------------------------------------ order report

    /** Validated filters of the order report. Dates refer to the order creation date. */
    public record OrderReportFilter(UUID workspaceId, LocalDate from, LocalDate to, String timezone,
                                    UUID clientId, OrderStatus status, PaymentState paymentState) {
    }

    private static String orderWhere(OrderReportFilter f, MapSqlParameterSource params) {
        params.addValue("ws", f.workspaceId()).addValue("from", f.from()).addValue("to", f.to())
                .addValue("tz", f.timezone());
        StringBuilder where = new StringBuilder(
                " WHERE o.workspace_id = :ws AND (o.created_at AT TIME ZONE :tz)::date BETWEEN :from AND :to");
        if (f.clientId() != null) {
            where.append(" AND o.client_id = :client");
            params.addValue("client", f.clientId());
        }
        if (f.status() != null) {
            where.append(" AND o.status = :status");
            params.addValue("status", f.status().name());
        }
        if (f.paymentState() != null) {
            where.append(switch (f.paymentState()) {
                case UNPAID -> " AND coalesce(pay.paid, 0) = 0";
                case PARTIALLY_PAID -> " AND coalesce(pay.paid, 0) > 0 AND coalesce(pay.paid, 0) < o.total_amount";
                case PAID -> " AND coalesce(pay.paid, 0) >= o.total_amount";
            });
        }
        return where.toString();
    }

    private static final String ORDER_ROW_SELECT = """
            SELECT o.id, o.order_number, (o.created_at AT TIME ZONE :tz)::date AS created_date,
                   c.name AS client_name, o.title, o.status, o.currency, o.total_amount,
                   coalesce(pay.paid, 0) AS paid, o.deadline, u.full_name AS assignee,
                   o.archived_at IS NOT NULL AS archived
            FROM customer_orders o
            JOIN clients c ON c.id = o.client_id
            LEFT JOIN users u ON u.id = o.assigned_user_id
            """;

    private static OrderReportRow orderRow(ResultSet rs) throws SQLException {
        BigDecimal total = rs.getBigDecimal("total_amount");
        BigDecimal paid = rs.getBigDecimal("paid");
        return new OrderReportRow(rs.getObject("id", UUID.class), rs.getString("order_number"),
                rs.getObject("created_date", LocalDate.class), rs.getString("client_name"), rs.getString("title"),
                OrderStatus.valueOf(rs.getString("status")), rs.getString("currency").trim(), total, paid,
                total.subtract(paid), PaymentState.of(paid, total), rs.getObject("deadline", LocalDate.class),
                rs.getString("assignee"), rs.getBoolean("archived"));
    }

    public List<OrderReportRow> orderRows(OrderReportFilter filter, int limit, int offset) {
        var params = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        String sql = ORDER_ROW_SELECT + PAID_PER_ORDER + orderWhere(filter, params)
                + " ORDER BY o.created_at DESC, o.id LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, i) -> orderRow(rs));
    }

    /** Calls the consumer for every row without keeping all rows in memory (CSV export). */
    public void streamOrderRows(OrderReportFilter filter, int maxRows, Consumer<OrderReportRow> consumer) {
        var params = new MapSqlParameterSource().addValue("limit", maxRows);
        String sql = ORDER_ROW_SELECT + PAID_PER_ORDER + orderWhere(filter, params)
                + " ORDER BY o.created_at DESC, o.id LIMIT :limit";
        jdbc.query(sql, params, (ResultSet rs) -> {
            consumer.accept(orderRow(rs));
        });
    }

    public List<OrderSummary> orderSummary(OrderReportFilter filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT o.currency, count(*) AS n, sum(o.total_amount) AS total,"
                + " sum(coalesce(pay.paid, 0)) AS paid,"
                + " sum(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount - coalesce(pay.paid, 0) ELSE 0 END)"
                + " AS balance FROM customer_orders o " + PAID_PER_ORDER + orderWhere(filter, params)
                + " GROUP BY o.currency ORDER BY o.currency";
        return jdbc.query(sql, params, (rs, i) -> new OrderSummary(rs.getString("currency").trim(), rs.getLong("n"),
                rs.getBigDecimal("total"), rs.getBigDecimal("paid"), rs.getBigDecimal("balance")));
    }

    public long countOrderRows(OrderReportFilter filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT count(*) FROM customer_orders o " + PAID_PER_ORDER + orderWhere(filter, params);
        return jdbc.queryForObject(sql, params, Long.class);
    }

    // ------------------------------------------------------------------ payment report

    /** Validated filters of the payment report. Dates refer to the payment date. */
    public record PaymentReportFilter(UUID workspaceId, LocalDate from, LocalDate to, UUID clientId,
                                      Payment.Method method) {
    }

    private static String paymentWhere(PaymentReportFilter f, MapSqlParameterSource params) {
        params.addValue("ws", f.workspaceId()).addValue("from", f.from()).addValue("to", f.to());
        StringBuilder where = new StringBuilder(
                " WHERE p.workspace_id = :ws AND p.payment_date BETWEEN :from AND :to");
        if (f.clientId() != null) {
            where.append(" AND o.client_id = :client");
            params.addValue("client", f.clientId());
        }
        if (f.method() != null) {
            where.append(" AND p.payment_method = :method");
            params.addValue("method", f.method().name());
        }
        return where.toString();
    }

    private static final String PAYMENT_ROW_SELECT = """
            SELECT p.id, p.payment_date, o.id AS order_id, o.order_number, c.name AS client_name,
                   p.amount, o.currency, p.payment_method, p.reference, u.full_name AS recorded_by,
                   p.voided_at IS NOT NULL AS voided, p.void_reason
            FROM payments p
            JOIN customer_orders o ON o.id = p.order_id
            JOIN clients c ON c.id = o.client_id
            LEFT JOIN users u ON u.id = p.created_by
            """;

    private static PaymentReportRow paymentRow(ResultSet rs) throws SQLException {
        return new PaymentReportRow(rs.getObject("id", UUID.class), rs.getObject("payment_date", LocalDate.class),
                rs.getObject("order_id", UUID.class), rs.getString("order_number"), rs.getString("client_name"),
                rs.getBigDecimal("amount"), rs.getString("currency").trim(),
                Payment.Method.valueOf(rs.getString("payment_method")), rs.getString("reference"),
                rs.getString("recorded_by"), rs.getBoolean("voided"), rs.getString("void_reason"));
    }

    public List<PaymentReportRow> paymentRows(PaymentReportFilter filter, int limit, int offset) {
        var params = new MapSqlParameterSource().addValue("limit", limit).addValue("offset", offset);
        String sql = PAYMENT_ROW_SELECT + paymentWhere(filter, params)
                + " ORDER BY p.payment_date DESC, p.created_at DESC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, i) -> paymentRow(rs));
    }

    public void streamPaymentRows(PaymentReportFilter filter, int maxRows, Consumer<PaymentReportRow> consumer) {
        var params = new MapSqlParameterSource().addValue("limit", maxRows);
        String sql = PAYMENT_ROW_SELECT + paymentWhere(filter, params)
                + " ORDER BY p.payment_date DESC, p.created_at DESC LIMIT :limit";
        jdbc.query(sql, params, (ResultSet rs) -> {
            consumer.accept(paymentRow(rs));
        });
    }

    public long countPaymentRows(PaymentReportFilter filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT count(*) FROM payments p JOIN customer_orders o ON o.id = p.order_id"
                + paymentWhere(filter, params);
        return jdbc.queryForObject(sql, params, Long.class);
    }

    /** Totals of valid (not voided) payments per currency. */
    public List<CurrencyAmount> paymentSummary(PaymentReportFilter filter) {
        var params = new MapSqlParameterSource();
        String sql = "SELECT o.currency, sum(p.amount) AS amount, count(*) AS n"
                + " FROM payments p JOIN customer_orders o ON o.id = p.order_id"
                + paymentWhere(filter, params) + " AND p.voided_at IS NULL GROUP BY o.currency ORDER BY o.currency";
        return jdbc.query(sql, params, (rs, i) ->
                new CurrencyAmount(rs.getString("currency").trim(), rs.getBigDecimal("amount"), rs.getLong("n")));
    }
}
