package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.PushDevice;

/**
 * Push delivery to an app installation through a relay.
 *
 * <p>The mirror image of {@link PushSenderPort}, and separate from it on purpose: a browser
 * subscription is written to directly and encrypted with keys only that browser holds, while a
 * relay token is handed to a third party that routes the message on. They share an outcome
 * vocabulary and nothing else.
 */
public interface RelayPushSenderPort {

    enum Outcome {
        /** The relay accepted the message. Not the same as received: that is a receipt. */
        DELIVERED,
        /**
         * The relay says this token belongs to an installation that no longer exists.
         *
         * <p>Reserved for exactly that. Anything else about the message is a FAILED, because a
         * transient condition must not be mistaken for a reason to stop writing to the token.
         */
        GONE,
        FAILED
    }

    record Result(Outcome outcome, String detail) {
    }

    /** False when no credential is configured, in which case nothing is sent. */
    boolean isConfigured();

    Result send(String token, String title, String body, String path);
}