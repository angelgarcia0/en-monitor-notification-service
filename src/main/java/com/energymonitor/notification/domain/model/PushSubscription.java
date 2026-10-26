package com.energymonitor.notification.domain.model;

/**
 * A browser that accepted Web Push for a user (the {@code PushSubscription} of the Push API).
 *
 * @param endpoint push service URL, unique per browser installation
 * @param p256dh   the browser's P-256 public key, base64url (65 bytes uncompressed)
 * @param auth     the shared authentication secret, base64url (16 bytes)
 */
public record PushSubscription(String idSubscription, String userId, String endpoint, String p256dh, String auth) {

    public PushSubscription {
        Preconditions.text(idSubscription, 10, "idSubscription");
        Preconditions.text(userId, 10, "userId");
        Preconditions.text(endpoint, 500, "endpoint");
        Preconditions.text(p256dh, 100, "p256dh");
        Preconditions.text(auth, 50, "auth");
        if (!endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("endpoint must be an https URL");
        }
    }
}
