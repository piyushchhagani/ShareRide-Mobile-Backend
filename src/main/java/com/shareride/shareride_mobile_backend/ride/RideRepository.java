package com.shareride.shareride_mobile_backend.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByDriverId(Long driverId);

    List<Ride> findByStatus(String status);

    List<Ride> findByStatusOrderByDepartureTimeAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);
}