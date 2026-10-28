package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.domain.model.Notification;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * A user's in-app activity notifications: listed, optionally of one home, marked read and
 * deleted.
 *
 * <p>Every call is scoped to the caller, so a user never reaches another user's notifications.
 * A user who left a home, or whose home was deleted, keeps the notifications they received about
 * it: they are theirs.
 */
@Component
public class ActivityInbox {

    /** Most notifications returned by {@link #list}. */
    public static final int LIST_LIMIT = 100;

    private final ActivityNotificationPort notifications;
    private final Clock clock;

    public ActivityInbox(ActivityNotificationPort notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * @param userId the caller
     * @param homeId one home, or null for all of them and those of no home
     * @return the caller's notifications, newest first, at most {@link #LIST_LIMIT}
     */
    public List<Notification> list(String userId, String homeId) {
        return notifications.listForUser(userId, blankToNull(homeId), LIST_LIMIT);
    }

    /**
     * @throws NoSuchElementException when the notification is not the caller's
     */
    public void markRead(String userId, String idNotification) {
        requireOwn(userId, idNotification);
        notifications.markRead(idNotification, LocalDateTime.now(clock));
    }

    /**
     * @param homeId one home, or null for all of them and those of no home
     * @return how many were marked
     */
    public int markAllRead(String userId, String homeId) {
        return notifications.markAllRead(userId, blankToNull(homeId), LocalDateTime.now(clock));
    }

    /**
     * @throws NoSuchElementException when the notification is not the caller's
     */
    public void delete(String userId, String idNotification) {
        requireOwn(userId, idNotification);
        notifications.delete(idNotification, LocalDateTime.now(clock));
    }

    private void requireOwn(String userId, String idNotification) {
        if (notifications.findForUser(userId, idNotification).isEmpty()) {
            throw new NoSuchElementException("no notification " + idNotification);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
