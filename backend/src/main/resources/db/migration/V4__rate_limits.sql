-- Fixed-window rate limit counters (login, registration, emails).
-- Kept in PostgreSQL because backend instances are stateless and may scale out.
-- "key" looks like 'login:ip:203.0.113.5' or 'reset:email:<sha256>'.

CREATE TABLE rate_limits (
    key           VARCHAR(150) PRIMARY KEY,
    window_start  TIMESTAMPTZ  NOT NULL,
    attempts      INTEGER      NOT NULL CHECK (attempts >= 0)
);
CREATE INDEX rate_limits_window_idx ON rate_limits (window_start);
