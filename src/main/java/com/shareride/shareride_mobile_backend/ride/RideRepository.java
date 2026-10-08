package com.shareride.shareride_mobile_backend.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByDriverId(Long driverId);

    List<Ride> findByStatus(String status);

    List<Ride> findByStatusOrderByDepartureTimeAsc(String status);
}