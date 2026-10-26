package com.energymonitor.notification.adapter.in.web;

import com.energymonitor.notification.adapter.in.web.dto.AdminNotificationDto;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.energymonitor.notification.application.port.out.UserProfileQuery;
import com.energymonitor.notification.application.usecase.AdminNotifications;
import com.energymonitor.notification.domain.model.Notification;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's administrator notifications.
 *
 * <p>The filter chain lets only callers holding {@code USER.READ} reach these routes; each one
 * then works on the caller's own notifications only.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationsController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminNotificationsController.class);

    private final AdminNotifications adminNotifications;
    private final UserProfileQuery profiles;
    private final CurrentUserResolver currentUser;

    public AdminNotificationsController(AdminNotifications adminNotifications,
                                        UserProfileQuery profiles, CurrentUserResolver currentUser) {
        this.adminNotifications = adminNotifications;
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AdminNotificationDto> list() {
        List<Notification> found = adminNotifications.list(currentUser.resolveCurrentUserId());
        Map<String, UserProfile> subjects = subjects(found.stream()
                .map(Notification::sourceId).filter(Objects::nonNull).distinct().toList());
        return found.stream().map(n -> toDto(n, subjects.get(n.sourceId()))).toList();
    }

    /** The notifications are listed without names while the security service does not answer. */
    private Map<String, UserProfile> subjects(List<String> userIds) {
        try {
            return profiles.findByIds(userIds);
        } catch (SecurityServiceUnavailableException unavailable) {
            LOG.warn("Administrator notifications listed without names: {}", unavailable.getMessage());
            return Map.of();
        }
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String id) {
        adminNotifications.markRead(currentUser.resolveCurrentUserId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        adminNotifications.delete(currentUser.resolveCurrentUserId(), id);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", "notification not found");
    }

    private static AdminNotificationDto toDto(Notification n, UserProfile subject) {
        String name = subject == null ? null : Stream.of(subject.name(), subject.lastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return new AdminNotificationDto(n.idNotification(), n.messageKey(), n.sourceId(), name,
                subject == null ? null : subject.email(), n.sentAt(), n.readAt() != null);
    }
}
