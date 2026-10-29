package kz.clientflow.auth;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.ApiException;

/**
 * Fixed-window rate limiter stored in PostgreSQL, so the limit is shared by
 * all backend instances. One atomic upsert per attempt.
 */
@Component
public class RateLimiter {

    private static final String CONSUME_SQL = """
            INSERT INTO rate_limits (key, window_start, attempts)
            VALUES (?, now(), 1)
            ON CONFLICT (key) DO UPDATE SET
                attempts = CASE WHEN rate_limits.window_start < now() - make_interval(secs => ?)
                                THEN 1 ELSE rate_limits.attempts + 1 END,
                window_start = CASE WHEN rate_limits.window_start < now() - make_interval(secs => ?)
                                    THEN now() ELSE rate_limits.window_start END
            RETURNING attempts
            """;

    private final JdbcTemplate jdbc;

    public RateLimiter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records one attempt and throws 429 when the limit is exceeded.
     * Runs in its own transaction so the attempt is counted even if the
     * calling operation fails afterwards (e.g. wrong password).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String key, int limit, Duration window) {
        long seconds = window.toSeconds();
        Integer attempts = jdbc.queryForObject(CONSUME_SQL, Integer.class, key, seconds, seconds);
        if (attempts != null && attempts > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
    }
}
