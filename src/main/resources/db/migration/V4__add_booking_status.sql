ALTER TABLE ride_requests
ADD COLUMN responded_at TIMESTAMP;

CREATE INDEX idx_ride_requests_ride_status
ON ride_requests(ride_id, status);