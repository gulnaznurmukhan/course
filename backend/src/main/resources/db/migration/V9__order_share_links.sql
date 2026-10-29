-- Read-only links that let a customer see the status of their order without an account.
-- Only the SHA-256 hash of the token is stored. A link expires and can be revoked.
CREATE TABLE order_share_links (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    order_id      UUID        NOT NULL,
    token_hash    CHAR(64)    NOT NULL UNIQUE,
    created_by    UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT order_share_links_order_fk FOREIGN KEY (order_id, workspace_id)
        REFERENCES customer_orders (id, workspace_id) ON DELETE CASCADE
);
CREATE INDEX order_share_links_order_idx ON order_share_links (order_id);
