package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Output port for the in-app activity notifications: what happened to a user's account, homes
 * and devices, told to the people it concerns.
 *
 * <p>They are rows of the same log as mail and push, on channel {@code IN_APP} with source
 * {@code ACCOUNT}, {@code HOME} or {@code DEVICE}, carrying the names their text is built with.
 * Every lookup here is restricted to those rows.
 */
public interface ActivityNotificationPort {

    /**
     * @param notification an in-app activity notification, already marked as delivered
     */
    void save(Notification notification);

    /**
     * @param userId the recipient
     * @param homeId one home, or null for all of them and those of no home
     * @param limit  how many to return
     * @return the recipient's activity notifications that are not deleted, newest first
     */
    List<Notification> listForUser(String userId, String homeId, int limit);

    /**
     * @param userId         the recipient
     * @param idNotification the notification
     * @return it, when it belongs to that recipient and is not deleted
     */
    Optional<Notification> findForUser(String userId, String idNotification);

    /** Stamps the read instant, once. */
    void markRead(String idNotification, LocalDateTime readAt);

    /**
     * Stamps the read instant on every unread activity notification of a recipient.
     *
     * @param homeId one home, or null for all of them and those of no home
     * @return how many were marked
     */
    int markAllRead(String userId, String homeId, LocalDateTime readAt);

    /** Soft-deletes the notification. */
    void delete(String idNotification, LocalDateTime deletedAt);
}
