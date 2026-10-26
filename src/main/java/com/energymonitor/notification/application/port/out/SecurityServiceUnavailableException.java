package com.energymonitor.notification.application.port.out;

/**
 * The security service did not answer, or answered with an error. Each caller decides how to
 * degrade: an alert is mailed to nobody and still pushed, an administration route answers 503,
 * and a message consumer lets the broker retry.
 */
public class SecurityServiceUnavailableException extends RuntimeException {

    public SecurityServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
