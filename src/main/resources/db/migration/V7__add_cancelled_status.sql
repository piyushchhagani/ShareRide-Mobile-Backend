ALTER TABLE ride_requests
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

ALTER TABLE rides
    DROP CONSTRAINT IF EXISTS chk_available_seats;

ALTER TABLE rides
    ADD CONSTRAINT chk_available_seats CHECK (available_seats >= 0);
