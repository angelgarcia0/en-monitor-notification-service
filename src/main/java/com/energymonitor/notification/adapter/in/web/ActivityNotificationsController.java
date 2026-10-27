package com.energymonitor.notification.adapter.in.web;

import com.energymonitor.notification.adapter.in.web.dto.ActivityNotificationDto;
import com.energymonitor.notification.application.usecase.ActivityInbox;
import com.energymonitor.notification.domain.model.Notification;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's activity notifications: what happened to their account, homes and devices.
 *
 * <p>Listed with the alerts and recommendations of each home by the apps; {@code homeId} keeps
 * only those of one home. Any signed-in account; every call works on the caller's own
 * notifications only.
 */
@RestController
@RequestMapping("/api/v1/notifications/activity")
public class ActivityNotificationsController {

    private final ActivityInbox inbox;
    private final CurrentUserResolver currentUser;

    public ActivityNotificationsController(ActivityInbox inbox, CurrentUserResolver currentUser) {
        this.inbox = inbox;
        this.currentUser = currentUser;
    }

    /** The newest 100, of one home when {@code homeId} is given. */
    @GetMapping
    public List<ActivityNotificationDto> list(@RequestParam(required = false) String homeId) {
        return inbox.list(currentUser.resolveCurrentUserId(), homeId).stream()
                .map(ActivityNotificationsController::toDto)
                .toList();
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable String id) {
        inbox.markRead(currentUser.resolveCurrentUserId(), id);
    }

    /** Marks every unread one read, of one home when {@code homeId} is given. */
    @PutMapping("/read")
    public Map<String, Integer> markAllRead(@RequestParam(required = false) String homeId) {
        return Map.of("read", inbox.markAllRead(currentUser.resolveCurrentUserId(), homeId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        inbox.delete(currentUser.resolveCurrentUserId(), id);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException e) {
        return Map.of("error", "notification not found");
    }

    private static ActivityNotificationDto toDto(Notification n) {
        return new ActivityNotificationDto(n.idNotification(), n.sourceType().name(), n.messageKey(),
                n.homeId(), n.messageParams(), n.sentAt(), n.readAt() != null);
    }
}
