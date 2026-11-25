package com.shareride.shareride_mobile_backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRideRequestId(Long rideRequestId);

    @Query("""
            select p from Payment p
            where p.rideRequest.passenger.id = :userId
               or p.rideRequest.ride.driver.id = :userId
            order by p.createdAt desc
            """)
    List<Payment> findAllByUserId(@Param("userId") Long userId);
}
