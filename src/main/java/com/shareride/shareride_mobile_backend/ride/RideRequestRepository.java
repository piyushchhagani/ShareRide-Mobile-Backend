package com.shareride.shareride_mobile_backend.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RideRequestRepository
        extends JpaRepository<RideRequest, Long> {

    List<RideRequest> findByPassengerId(Long passengerId);

    List<RideRequest> findByRideId(Long rideId);

    List<RideRequest> findByRideIdAndStatus(
            Long rideId,
            String status
    );

    Optional<RideRequest> findByRideIdAndPassengerId(
            Long rideId,
            Long passengerId
    );
}