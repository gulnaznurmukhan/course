-- ClientFlow core schema.
-- Runs inside the "clientflow" schema (Flyway sets search_path).
-- Money: NUMERIC(14,2). Timestamps: TIMESTAMPTZ (stored in UTC).

-- ---------------------------------------------------------------------------
-- Users and workspaces
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    preferred_language  VARCHAR(2)   NOT NULL DEFAULT 'ru',
    theme               VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    notify_deadlines     BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_overdue       BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_order_changes BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_payments      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT users_language_check  CHECK (preferred_language IN ('kk', 'ru', 'en')),
    CONSTRAINT users_theme_check     CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM'))
);
CREATE UNIQUE INDEX users_email_uk ON users (email);

CREATE TABLE workspaces (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100) NOT NULL,
    -- Next human-readable order number (ORD-000001). Incremented under a row lock.
    next_order_number  INTEGER      NOT NULL DEFAULT 1 CHECK (next_order_number > 0),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workspace_memberships (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role          VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'EMPLOYEE')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workspace_memberships_uk UNIQUE (workspace_id, user_id)
);
CREATE INDEX workspace_memberships_user_idx ON workspace_memberships (user_id);

-- ---------------------------------------------------------------------------
-- Auth tokens (only SHA-256 hashes of the tokens are stored)
-- ---------------------------------------------------------------------------

CREATE TABLE verification_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX verification_tokens_user_idx ON verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------------------
-- Clients
-- ---------------------------------------------------------------------------

CREATE TABLE clients (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(254),
    phone         VARCHAR(30),
    company       VARCHAR(150),
    address       VARCHAR(300),
    notes         VARCHAR(5000),
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Allows composite foreign keys that guarantee same-workspace relations
    CONSTRAINT clients_id_workspace_uk UNIQUE (id, workspace_id)
);
CREATE INDEX clients_workspace_idx ON clients (workspace_id, archived_at);
CREATE INDEX clients_workspace_email_idx ON clients (workspace_id, lower(email));
CREATE INDEX clients_workspace_phone_idx ON clients (workspace_id, phone);

-- ---------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------

CREATE TABLE customer_orders (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    client_id         UUID          NOT NULL,
    order_number      VARCHAR(20)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(5000),
    total_amount      NUMERIC(14, 2) NOT NULL CHECK (total_amount > 0),
    currency          CHAR(3)       NOT NULL CHECK (currency IN ('KZT', 'RUB', 'USD', 'EUR')),
    status            VARCHAR(12)   NOT NULL DEFAULT 'NEW'
                      CHECK (status IN ('NEW', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    deadline          DATE,
    assigned_user_id  UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_by        UUID          REFERENCES users (id) ON DELETE SET NULL,
    archived_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT customer_orders_number_uk UNIQUE (workspace_id, order_number),
    CONSTRAINT customer_orders_id_workspace_uk UNIQUE (id, workspace_id),
    -- The client must belong to the same workspace as the order
    CONSTRAINT customer_orders_client_fk FOREIGN KEY (client_id, workspace_id)
        REFERENCES clients (id, workspace_id),
    -- Only finished orders can be archived
    CONSTRAINT customer_orders_archive_check
        CHECK (archived_at IS NULL OR status IN ('COMPLETED', 'CANCELLED'))
);
CREATE INDEX customer_orders_workspace_status_idx ON customer_orders (workspace_id, status);
CREATE INDEX customer_orders_workspace_deadline_idx ON customer_orders (workspace_id, deadline);
CREATE INDEX customer_orders_client_idx ON customer_orders (client_id);
CREATE INDEX customer_orders_assigned_idx ON customer_orders (assigned_user_id);

CREATE TABLE order_notes (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID          NOT NULL,
    order_id        UUID          NOT NULL,
    author_user_id  UUID          REFERENCES users (id) ON DELETE SET NULL,
    body            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT order_notes_order_fk FOREIGN KEY (order_id, workspace_id)
        REFERENCES customer_orders (id, workspace_id) ON DELETE CASCADE
);
CREATE INDEX order_notes_order_idx ON order_notes (order_id, created_at);

-- ---------------------------------------------------------------------------
-- Payments. Never updated except for voiding; the balance is always computed:
--   paid = SUM(amount) WHERE voided_at IS NULL;  balance = total_amount - paid
-- ---------------------------------------------------------------------------

CREATE TABLE payments (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     UUID          NOT NULL,
    order_id         UUID          NOT NULL,
    amount           NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    payment_method   VARCHAR(15)   NOT NULL
                     CHECK (payment_method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    payment_date     DATE          NOT NULL,
    reference        VARCHAR(100),
    -- Sent by the client once per submission; protects against double submits
    idempotency_key  UUID          NOT NULL,
    created_by       UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    voided_at        TIMESTAMPTZ,
    voided_by        UUID          REFERENCES users (id) ON DELETE SET NULL,
    void_reason      VARCHAR(300),
    CONSTRAINT payments_order_fk FOREIGN KEY (order_id, workspace_id)
        REFERENCES customer_orders (id, workspace_id),
    CONSTRAINT payments_idempotency_uk UNIQUE (workspace_id, idempotency_key),
    CONSTRAINT payments_void_check CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);
CREATE INDEX payments_order_idx ON payments (order_id);
CREATE INDEX payments_workspace_date_idx ON payments (workspace_id, payment_date);

-- ---------------------------------------------------------------------------
-- Notifications and activity log
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          VARCHAR(30)  NOT NULL
                  CHECK (type IN ('DEADLINE_APPROACHING', 'ORDER_OVERDUE', 'ORDER_CHANGED', 'PAYMENT_REMINDER')),
    order_id      UUID         REFERENCES customer_orders (id) ON DELETE CASCADE,
    -- Parameters for the translated message text (rendered by the frontend)
    payload       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- Prevents duplicate reminders, e.g. 'DEADLINE:<orderId>:<deadline>'
    dedup_key     VARCHAR(150),
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notifications_dedup_uk UNIQUE (user_id, dedup_key)
);
CREATE INDEX notifications_user_idx ON notifications (user_id, read_at, created_at DESC);

CREATE TABLE activity_logs (
    id             BIGSERIAL    PRIMARY KEY,
    workspace_id   UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    actor_user_id  UUID         REFERENCES users (id) ON DELETE SET NULL,
    action         VARCHAR(50)  NOT NULL,
    entity_type    VARCHAR(30)  NOT NULL,
    entity_id      UUID,
    metadata       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX activity_logs_workspace_idx ON activity_logs (workspace_id, created_at DESC);
CREATE INDEX activity_logs_entity_idx ON activity_logs (entity_type, entity_id);
