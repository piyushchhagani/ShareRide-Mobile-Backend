package com.shareride.shareride_mobile_backend.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByRatedUserIdOrderByCreatedAtDesc(Long ratedUserId);

    List<Rating> findByRaterIdOrderByCreatedAtDesc(Long raterId);

    List<Rating> findByRideIdOrderByCreatedAtDesc(Long rideId);

    boolean existsByRideIdAndRaterIdAndRatedUserId(Long rideId, Long raterId, Long ratedUserId);

    @Query("select avg(r.score) from Rating r where r.ratedUser.id = :userId")
    Double findAverageScoreByRatedUserId(@Param("userId") Long userId);

    long countByRatedUserId(Long ratedUserId);
}
