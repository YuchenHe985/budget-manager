-- Categories and the ledger. Money is stored as integer cents; the kind gives the direction.
CREATE TABLE category (
    id    INTEGER PRIMARY KEY,
    name  TEXT    NOT NULL COLLATE NOCASE UNIQUE CHECK (length(name) BETWEEN 1 AND 40),
    grp   TEXT    NOT NULL CHECK (grp IN ('NEEDS', 'WANTS', 'SAVINGS'))
);

CREATE TABLE txn (
    id           INTEGER PRIMARY KEY,
    date         TEXT    NOT NULL CHECK (date GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]-[0-3][0-9]'),
    kind         TEXT    NOT NULL CHECK (kind IN ('EXPENSE', 'REFUND', 'INCOME')),
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0 AND amount_cents <= 100000000000),
    category_id  INTEGER REFERENCES category (id) ON DELETE RESTRICT,
    description  TEXT    NOT NULL DEFAULT '' CHECK (length(description) <= 200),
    CHECK ((kind = 'INCOME') = (category_id IS NULL))
);

CREATE INDEX txn_by_date ON txn (date);
CREATE INDEX txn_by_category ON txn (category_id);

INSERT INTO category (name, grp) VALUES
    ('Housing', 'NEEDS'),
    ('Food', 'NEEDS'),
    ('Transportation', 'NEEDS'),
    ('Education', 'NEEDS'),
    ('Entertainment', 'WANTS'),
    ('Shopping', 'WANTS'),
    ('Emergency Fund', 'SAVINGS');
