package kz.clientflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic background work:
 * 1. create reminder notifications (deadlines, overdue orders, unpaid balances);
 * 2. delete auth tokens and rate-limit counters that are no longer needed.
 *
 * Triggered every hour by the scheduler (always-on servers) and by an external
 * cron call (serverless hosting such as Vercel, where the container may sleep).
 * Running it more often than needed is safe.
 */
@Component
public class MaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJob.class);

    private final ReminderGenerator reminders;
    private final JdbcTemplate jdbc;

    public MaintenanceJob(ReminderGenerator reminders, JdbcTemplate jdbc) {
        this.reminders = reminders;
        this.jdbc = jdbc;
    }

    public record Result(int notificationsCreated, int tokensDeleted, int rateLimitsDeleted) {
    }

    @Transactional
    public Result run() {
        int created = reminders.generate();
        int tokens = jdbc.update("DELETE FROM verification_tokens WHERE expires_at < now() - interval '7 days'")
                + jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at < now() - interval '7 days'");
        int limits = jdbc.update("DELETE FROM rate_limits WHERE window_start < now() - interval '1 day'");
        log.info("Maintenance: {} notifications, {} old tokens and {} rate-limit rows removed", created, tokens, limits);
        return new Result(created, tokens, limits);
    }
}
