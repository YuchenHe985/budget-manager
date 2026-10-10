-- Per-month spending limits and user settings (the allocation rule was fixed at 50/30/20 before).
CREATE TABLE monthly_budget (
    month       TEXT    NOT NULL CHECK (month GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'),
    category_id INTEGER NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    limit_cents INTEGER NOT NULL CHECK (limit_cents >= 0 AND limit_cents <= 100000000000),
    PRIMARY KEY (month, category_id)
);

CREATE TABLE setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO setting (key, value) VALUES
    ('allocation.needs', '50'),
    ('allocation.wants', '30'),
    ('allocation.savings', '20');
