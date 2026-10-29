-- Service catalog (price list) and order line items.

CREATE TABLE services (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000),
    unit          VARCHAR(20)   NOT NULL,
    price         NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    currency      CHAR(3)       NOT NULL CHECK (currency IN ('KZT', 'RUB', 'USD', 'EUR')),
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX services_workspace_idx ON services (workspace_id, archived_at);

-- A line item is a snapshot: name and price are copied when the item is saved,
-- so later price-list changes never alter existing orders.
-- line_total = quantity * unit_price (rounded to 2 decimals, calculated by the backend).
CREATE TABLE order_items (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    order_id      UUID          NOT NULL,
    service_id    UUID          REFERENCES services (id) ON DELETE SET NULL,
    position      INTEGER       NOT NULL CHECK (position >= 0),
    name          VARCHAR(200)  NOT NULL,
    unit          VARCHAR(20)   NOT NULL,
    quantity      NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    line_total    NUMERIC(14, 2) NOT NULL CHECK (line_total >= 0),
    CONSTRAINT order_items_order_fk FOREIGN KEY (order_id, workspace_id)
        REFERENCES customer_orders (id, workspace_id) ON DELETE CASCADE
);
CREATE INDEX order_items_order_idx ON order_items (order_id, position);
