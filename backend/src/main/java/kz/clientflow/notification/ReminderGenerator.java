package kz.clientflow.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates reminder notifications for all workspaces with a few set-based SQL
 * statements (no orders are loaded into memory).
 *
 * Duplicates are impossible: every reminder has a dedup key and the database
 * has UNIQUE (user_id, dedup_key); "ON CONFLICT DO NOTHING" skips reminders that
 * already exist. Running the job twice, or on two servers at once, is harmless.
 *
 * Recipients:
 * - approaching deadline (within 2 days): the assignee, or OWNER/MANAGER if nobody is assigned;
 * - overdue: the assignee and OWNER/MANAGER (once per order and deadline);
 * - payment reminder: OWNER/MANAGER, once per week while a completed order has a balance.
 * Users who switched a notification type off in their settings are skipped.
 */
@Component
public class ReminderGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReminderGenerator.class);

    private static final String ACTIVE = "('NEW', 'CONFIRMED', 'IN_PROGRESS')";
    private static final String MANAGERS_OF_ORDER =
            "SELECT m.user_id FROM workspace_memberships m"
                    + " WHERE m.workspace_id = o.workspace_id AND m.role IN ('OWNER', 'MANAGER')";

    private static final String DEADLINE_SQL = """
            INSERT INTO notifications (workspace_id, user_id, type, order_id, payload, dedup_key)
            SELECT o.workspace_id, r.user_id, 'DEADLINE_APPROACHING', o.id,
                   jsonb_build_object('orderNumber', o.order_number, 'title', o.title, 'deadline', o.deadline),
                   'DEADLINE:' || o.id || ':' || o.deadline
            FROM customer_orders o
            JOIN LATERAL (
                SELECT o.assigned_user_id AS user_id WHERE o.assigned_user_id IS NOT NULL
                UNION
                %s AND o.assigned_user_id IS NULL
            ) r ON true
            JOIN users u ON u.id = r.user_id AND u.notify_deadlines
            WHERE o.archived_at IS NULL AND o.status IN %s
              AND o.deadline BETWEEN :today AND :soon
            ON CONFLICT (user_id, dedup_key) DO NOTHING
            """.formatted(MANAGERS_OF_ORDER, ACTIVE);

    private static final String OVERDUE_SQL = """
            INSERT INTO notifications (workspace_id, user_id, type, order_id, payload, dedup_key)
            SELECT o.workspace_id, r.user_id, 'ORDER_OVERDUE', o.id,
                   jsonb_build_object('orderNumber', o.order_number, 'title', o.title, 'deadline', o.deadline),
                   'OVERDUE:' || o.id || ':' || o.deadline
            FROM customer_orders o
            JOIN LATERAL (
                SELECT o.assigned_user_id AS user_id WHERE o.assigned_user_id IS NOT NULL
                UNION
                %s
            ) r ON true
            JOIN users u ON u.id = r.user_id AND u.notify_overdue
            WHERE o.archived_at IS NULL AND o.status IN %s AND o.deadline < :today
            ON CONFLICT (user_id, dedup_key) DO NOTHING
            """.formatted(MANAGERS_OF_ORDER, ACTIVE);

    private static final String PAYMENT_SQL = """
            INSERT INTO notifications (workspace_id, user_id, type, order_id, payload, dedup_key)
            SELECT o.workspace_id, r.user_id, 'PAYMENT_REMINDER', o.id,
                   jsonb_build_object('orderNumber', o.order_number, 'title', o.title,
                                      'balance', (o.total_amount - coalesce(pay.paid, 0))::text,
                                      'currency', o.currency),
                   'PAYMENT:' || o.id || ':' || :week
            FROM customer_orders o
            LEFT JOIN (SELECT order_id, sum(amount) AS paid FROM payments
                       WHERE voided_at IS NULL GROUP BY order_id) pay ON pay.order_id = o.id
            JOIN LATERAL (%s) r ON true
            JOIN users u ON u.id = r.user_id AND u.notify_payments
            WHERE o.archived_at IS NULL AND o.status = 'COMPLETED'
              AND o.total_amount > coalesce(pay.paid, 0)
            ON CONFLICT (user_id, dedup_key) DO NOTHING
            """.formatted(MANAGERS_OF_ORDER);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ReminderGenerator(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Returns the number of new notifications. */
    @Transactional
    public int generate() {
        LocalDate today = LocalDate.now(clock);
        String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        var params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("soon", today.plusDays(2))
                .addValue("week", week);

        int created = jdbc.update(DEADLINE_SQL, params)
                + jdbc.update(OVERDUE_SQL, params)
                + jdbc.update(PAYMENT_SQL, params);
        log.info("Reminder job created {} notifications", created);
        return created;
    }
}
