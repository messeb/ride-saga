CREATE TABLE rides (
    id                  VARCHAR(36)    PRIMARY KEY,
    rider_id            VARCHAR(36)    NOT NULL,
    pickup_location     VARCHAR(255)   NOT NULL,
    dropoff_location    VARCHAR(255)   NOT NULL,
    fare_amount         NUMERIC(10, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    requested_at        TIMESTAMPTZ    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    driver_id           VARCHAR(36),
    cancellation_reason VARCHAR(100)
);

CREATE INDEX idx_rides_status ON rides (status);
