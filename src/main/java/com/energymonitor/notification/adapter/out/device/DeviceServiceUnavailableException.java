package com.energymonitor.notification.adapter.out.device;

/**
 * The device service did not answer in time, or answered with an error, and no earlier answer
 * was kept to fall back on.
 */
public class DeviceServiceUnavailableException extends RuntimeException {

    public DeviceServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
