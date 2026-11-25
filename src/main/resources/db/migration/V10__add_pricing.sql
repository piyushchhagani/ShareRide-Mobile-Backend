ALTER TABLE rides
    ADD COLUMN IF NOT EXISTS price_per_seat DECIMAL(10,2);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    ride_request_id BIGINT NOT NULL UNIQUE REFERENCES ride_requests(id),
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_ride_request ON payments(ride_request_id);
CREATE INDEX idx_payments_status ON payments(status);
