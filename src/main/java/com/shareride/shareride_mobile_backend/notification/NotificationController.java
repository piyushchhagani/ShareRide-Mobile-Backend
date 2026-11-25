package com.shareride.shareride_mobile_backend.notification;

import com.shareride.shareride_mobile_backend.user.User;
import com.shareride.shareride_mobile_backend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        List<NotificationResponse> list = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(notificationService::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        long count = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{notificationId}/read")
    @Transactional
    public ResponseEntity<?> markRead(
            @PathVariable @NonNull Long notificationId,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);
        Notification notification = notificationRepository.findById(notificationId).orElse(null);

        if (notification == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Notification not found"));
        }
        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("message", "You cannot modify this notification"));
        }

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return ResponseEntity.ok(notificationService.toResponse(saved));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        int updated = notificationRepository.markAllReadByUserId(user.getId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
