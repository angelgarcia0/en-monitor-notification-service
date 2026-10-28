package com.energymonitor.notification.domain.model;

/**
 * An app installation that receives push through a relay.
 *
 * <p>Not the same thing as a {@link PushSubscription}: a browser subscription carries an https
 * endpoint and the keys its payload is encrypted with, and none of that exists here. The token
 * identifies the installation, so it is what a row is keyed by.
 *
 * <p>The token is not a credential. It authenticates nobody: showing it to anyone grants no
 * access, it only lets the relay deliver messages to that installation.
 */
public record PushDevice(String idDevice, String userId, String token, PushDevicePlatform platform) {

    /** Longest relay token seen, with headroom; the prefix alone is "ExponentPushToken[". */
    public static final int TOKEN_MAX_LENGTH = 255;

    public PushDevice {
        Preconditions.text(idDevice, 10, "idDevice");
        Preconditions.text(userId, 10, "userId");
        Preconditions.text(token, TOKEN_MAX_LENGTH, "token");
        Preconditions.notNull(platform, "platform");
    }
}