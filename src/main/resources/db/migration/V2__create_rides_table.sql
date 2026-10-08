CREATE TABLE rides (
    id BIGSERIAL PRIMARY KEY,

    driver_id BIGINT NOT NULL,

    pickup_address VARCHAR(255) NOT NULL,
    pickup_latitude DOUBLE PRECISION NOT NULL,
    pickup_longitude DOUBLE PRECISION NOT NULL,

    destination_address VARCHAR(255) NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,

    departure_time TIMESTAMP NOT NULL,

    available_seats INTEGER NOT NULL,

    vehicle_type VARCHAR(50),
    vehicle_number VARCHAR(50),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rides_driver
        FOREIGN KEY (driver_id)
        REFERENCES users(id),

    CONSTRAINT chk_available_seats
        CHECK (available_seats > 0)
);

CREATE INDEX idx_rides_driver_id
    ON rides(driver_id);

CREATE INDEX idx_rides_departure_time
    ON rides(departure_time);

CREATE INDEX idx_rides_status
    ON rides(status);