package com.energymonitor.notification.adapter.out.home;

/**
 * The home service did not answer in time, or answered with an error, and no earlier answer was
 * kept to fall back on. A message for that home is not sent now: the consumer lets the broker
 * retry it.
 */
public class HomeServiceUnavailableException extends RuntimeException {

    public HomeServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
