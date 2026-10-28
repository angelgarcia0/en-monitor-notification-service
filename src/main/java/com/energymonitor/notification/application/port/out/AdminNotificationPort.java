package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Output port for the in-app notifications addressed to administrators.
 *
 * <p>They are rows of the same log as mail and push, on channel {@code IN_APP} with source
 * {@code ADMINISTRATION}; every lookup here is restricted to those rows.
 */
public interface AdminNotificationPort {

    /**
     * @param notification an already delivered in-app notification
     */
    void save(Notification notification);

    /**
     * Whether an administrator already holds the notification an event produces. Events can
     * arrive more than once from the message broker, and this is what makes the second one a
     * no-op.
     *
     * @param userId     the administrator
     * @param messageKey the key of the message
     * @param sourceId   the account the event is about
     * @param sentAt     when the event happened, to the second
     * @return true when that notification was already written, deleted or not
     */
    boolean exists(String userId, String messageKey, String sourceId, LocalDateTime sentAt);

    /**
     * @param userId the administrator
     * @param limit  how many to return
     * @return the administrator's notifications that are not deleted, newest first
     */
    List<Notification> listForUser(String userId, int limit);

    /**
     * @param userId         the administrator
     * @param idNotification the notification
     * @return it, when it belongs to that administrator and is not deleted
     */
    Optional<Notification> findForUser(String userId, String idNotification);

    /** Stamps the read instant, once. */
    void markRead(String idNotification, LocalDateTime readAt);

    /** Soft-deletes the notification. */
    void delete(String idNotification, LocalDateTime deletedAt);
}
