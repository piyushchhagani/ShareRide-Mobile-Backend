ALTER TABLE ride_requests
    DROP CONSTRAINT uq_ride_passenger;

CREATE UNIQUE INDEX uq_ride_passenger_active
    ON ride_requests (ride_id, passenger_id)
    WHERE status IN ('PENDING', 'ACCEPTED');