package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.Notification;

/**
 * Output port for the in-app activity notifications: what happened to a user's account, homes
 * and devices, told to the people it concerns.
 *
 * <p>They are rows of the same log as mail and push, on channel {@code IN_APP} with source
 * {@code ACCOUNT}, {@code HOME} or {@code DEVICE}, carrying the names their text is built with.
 */
public interface ActivityNotificationPort {

    /**
     * @param notification an in-app activity notification, already marked as delivered
     */
    void save(Notification notification);
}
