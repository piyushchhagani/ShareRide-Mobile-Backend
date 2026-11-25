package com.shareride.shareride_mobile_backend.notification;

import com.shareride.shareride_mobile_backend.ride.Ride;
import com.shareride.shareride_mobile_backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void createNotification(
            User recipient,
            String type,
            String title,
            String message,
            Ride ride
    ) {
        if (recipient == null) return;

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRide(ride);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getRide() != null ? n.getRide().getId() : null,
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
