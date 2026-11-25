package com.shareride.shareride_mobile_backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByResetToken(String resetToken);

    boolean existsByEmail(String email);
}