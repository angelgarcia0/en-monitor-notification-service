package com.energymonitor.notification.application.port.out;

/**
 * Output port for generating the {@code VARCHAR(10)} identifier of a notification row.
 */
public interface NotificationIdentifierPort {

    /**
     * Generates a new notification identifier.
     *
     * @return a non-blank, at most 10 character identifier
     */
    String generate();
}
