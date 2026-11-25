package com.shareride.shareride_mobile_backend.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 20)
    private String role = "STUDENT";

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "ride_updates_enabled", nullable = false)
    private boolean rideUpdatesEnabled = true;

    @Column(name = "booking_updates_enabled", nullable = false)
    private boolean bookingUpdatesEnabled = true;

    @Column(name = "product_updates_enabled", nullable = false)
    private boolean productUpdatesEnabled = false;

    @Column(name = "location_assistance_enabled", nullable = false)
    private boolean locationAssistanceEnabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public boolean isRideUpdatesEnabled() {
        return rideUpdatesEnabled;
    }

    public void setRideUpdatesEnabled(boolean rideUpdatesEnabled) {
        this.rideUpdatesEnabled = rideUpdatesEnabled;
    }

    public boolean isBookingUpdatesEnabled() {
        return bookingUpdatesEnabled;
    }

    public void setBookingUpdatesEnabled(boolean bookingUpdatesEnabled) {
        this.bookingUpdatesEnabled = bookingUpdatesEnabled;
    }

    public boolean isProductUpdatesEnabled() {
        return productUpdatesEnabled;
    }

    public void setProductUpdatesEnabled(boolean productUpdatesEnabled) {
        this.productUpdatesEnabled = productUpdatesEnabled;
    }

    public boolean isLocationAssistanceEnabled() {
        return locationAssistanceEnabled;
    }

    public void setLocationAssistanceEnabled(boolean locationAssistanceEnabled) {
        this.locationAssistanceEnabled = locationAssistanceEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public User() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
}
}