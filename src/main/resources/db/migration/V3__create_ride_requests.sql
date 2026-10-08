CREATE TABLE ride_requests (
    id BIGSERIAL PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_seats INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ride_requests_ride
        FOREIGN KEY (ride_id) REFERENCES rides(id),

    CONSTRAINT fk_ride_requests_passenger
        FOREIGN KEY (passenger_id) REFERENCES users(id),

    CONSTRAINT chk_requested_seats
        CHECK (requested_seats > 0),

    CONSTRAINT uq_ride_passenger
        UNIQUE (ride_id, passenger_id)
);

CREATE INDEX idx_ride_requests_ride_id
    ON ride_requests(ride_id);

CREATE INDEX idx_ride_requests_passenger_id
    ON ride_requests(passenger_id);

CREATE INDEX idx_ride_requests_status
    ON ride_requests(status);