-- Pending invitations to a workspace.
-- If the invited email already belongs to a verified user, the membership is
-- created immediately and no invitation row is stored. Otherwise the row waits
-- until a user with this email confirms their address.
-- The API answers the same way in both cases, so it does not reveal accounts.

CREATE TABLE workspace_invitations (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email         VARCHAR(254) NOT NULL CHECK (email = lower(email)),
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('MANAGER', 'EMPLOYEE')),
    invited_by    UUID         REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT workspace_invitations_uk UNIQUE (workspace_id, email)
);
CREATE INDEX workspace_invitations_email_idx ON workspace_invitations (email);
