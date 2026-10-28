package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.PushSubscription;

/**
 * Sends one encrypted Web Push message to one browser.
 */
public interface PushSenderPort {

    enum Outcome {
        DELIVERED,
        /** The push service says the subscription no longer exists (404/410): forget it. */
        GONE,
        FAILED
    }

    record Result(Outcome outcome, String detail) {
    }

    boolean isConfigured();

    /** Base64url public VAPID key the browser needs to subscribe; empty when push is off. */
    String publicKey();

    Result send(PushSubscription subscription, String jsonPayload);
}
