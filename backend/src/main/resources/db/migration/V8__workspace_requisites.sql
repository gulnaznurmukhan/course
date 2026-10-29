-- Company requisites printed on invoices (Kazakhstan: BIN, IIK/IBAN, BIK).
ALTER TABLE workspaces
    ADD COLUMN legal_name VARCHAR(200),
    ADD COLUMN bin        VARCHAR(12)  CHECK (bin ~ '^[0-9]{12}$'),
    ADD COLUMN address    VARCHAR(300),
    ADD COLUMN phone      VARCHAR(30),
    ADD COLUMN email      VARCHAR(254),
    ADD COLUMN bank_name  VARCHAR(150),
    ADD COLUMN iban       VARCHAR(34)  CHECK (iban ~ '^[A-Z]{2}[0-9A-Z]{10,32}$'),
    ADD COLUMN bik        VARCHAR(11)  CHECK (bik ~ '^[A-Z0-9]{8,11}$');
