package com.energymonitor.notification.domain.model;

/**
 * Delivery status of the notification.
 */
public enum NotificationStatus {

    /** Notification is pending to be sent. */
    PENDING,

    /** Notification was sent successfully. */
    SENT,

    /** Notification failed to be sent. */
    FAILED
}
