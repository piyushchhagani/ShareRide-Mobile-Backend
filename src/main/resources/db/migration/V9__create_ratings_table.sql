CREATE TABLE ratings (
    id BIGSERIAL PRIMARY KEY,
    ride_id BIGINT NOT NULL REFERENCES rides(id),
    rater_id BIGINT NOT NULL REFERENCES users(id),
    rated_user_id BIGINT NOT NULL REFERENCES users(id),
    score INTEGER NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ride_id, rater_id, rated_user_id)
);

CREATE INDEX idx_ratings_rated_user ON ratings(rated_user_id);
CREATE INDEX idx_ratings_ride_id ON ratings(ride_id);
