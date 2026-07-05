CREATE TABLE payments (
    id         VARCHAR(36)    PRIMARY KEY,
    ride_id    VARCHAR(36)    NOT NULL UNIQUE,
    amount     NUMERIC(10, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    status     VARCHAR(20)    NOT NULL
);

-- idempotent-consumer ledger: the primary key makes double-processing impossible
CREATE TABLE processed_events (
    event_id     VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
