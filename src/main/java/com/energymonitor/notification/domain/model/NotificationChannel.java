package com.energymonitor.notification.domain.model;

/**
 * Channel used to deliver the notification.
 */
public enum NotificationChannel {

    /** Email delivery channel. */
    EMAIL,
    /** Web Push (RFC 8030) to a browser subscription. */
    PUSH,

    /** Shown inside the web app; never leaves the platform. */
    IN_APP
}
