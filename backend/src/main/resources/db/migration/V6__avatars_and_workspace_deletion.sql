-- 1) Profile pictures. Stored in PostgreSQL because the hosting container
--    filesystem is not persistent. Size is limited by the application (512 KB).
CREATE TABLE user_avatars (
    user_id       UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type  VARCHAR(20) NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp')),
    data          BYTEA       NOT NULL CHECK (octet_length(data) <= 524288),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) When a whole workspace is deleted (its owner deleted the account), its
--    payments are deleted with it. Payments of an existing workspace still
--    cannot be deleted: they are only voided.
ALTER TABLE payments
    ADD CONSTRAINT payments_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE;

ALTER TABLE order_notes
    ADD CONSTRAINT order_notes_workspace_fk FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE;
