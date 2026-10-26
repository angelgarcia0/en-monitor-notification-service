package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.application.port.out.PushDevicePort;
import com.energymonitor.notification.application.port.out.PushSenderPort;
import com.energymonitor.notification.application.port.out.PushSubscriptionPort;
import com.energymonitor.notification.application.port.out.RelayPushSenderPort;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.PushDevice;
import com.energymonitor.notification.domain.model.PushDevicePlatform;
import com.energymonitor.notification.domain.model.PushSubscription;
import org.springframework.stereotype.Component;

/**
 * A user's channel preferences and everywhere alerts can reach them: browsers that accepted Web
 * Push, and app installations that registered a relay token.
 */
@Component
public class ManageNotificationChannels {

    private final NotificationPreferencePort preferences;
    private final PushSubscriptionPort subscriptions;
    private final PushDevicePort devices;
    private final PushSenderPort push;
    private final RelayPushSenderPort relayPush;
    private final NotificationIdentifierPort identifiers;

    public ManageNotificationChannels(NotificationPreferencePort preferences, PushSubscriptionPort subscriptions,
                                      PushDevicePort devices, PushSenderPort push, RelayPushSenderPort relayPush,
                                      NotificationIdentifierPort identifiers) {
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.devices = devices;
        this.push = push;
        this.relayPush = relayPush;
        this.identifiers = identifiers;
    }

    public NotificationPreference preferences(String userId) {
        return preferences.find(userId).orElseGet(() -> NotificationPreference.defaults(userId));
    }

    public NotificationPreference update(String userId, boolean emailEnabled, boolean pushEnabled) {
        NotificationPreference updated = new NotificationPreference(userId, emailEnabled, pushEnabled);
        preferences.save(updated);
        return updated;
    }

    /**
     * Whether push is switched on at all.
     *
     * <p>True when either transport is configured, because the preference is a single switch: a user
     * who has only ever installed the app should not see push greyed out just because no VAPID key
     * is set on the server.
     */
    public boolean pushAvailable() {
        return push.isConfigured() || relayPush.isConfigured();
    }

    /** Null unless Web Push is configured; only browsers need a VAPID public key. */
    public String pushPublicKey() {
        return push.publicKey();
    }

    /**
     * Whether Web Push alone is configured.
     *
     * <p>Separate from {@link #pushAvailable()} on purpose: a relay credential alone satisfies the
     * preference without there being a VAPID key, and answering the browser's public-key request in
     * that state would hand it an empty key instead of a 404.
     */
    public boolean webPushAvailable() {
        return push.isConfigured();
    }

    /** Whether app installations can be reached right now. */
    public boolean appPushAvailable() {
        return relayPush.isConfigured();
    }

    public int browsers(String userId) {
        return subscriptions.findByUser(userId).size();
    }

    public int devices(String userId) {
        return devices.findByUser(userId).size();
    }

    public void subscribe(String userId, String endpoint, String p256dh, String auth) {
        String id = subscriptions.findByEndpoint(endpoint).map(PushSubscription::idSubscription)
                .orElseGet(identifiers::generate);
        subscriptions.save(new PushSubscription(id, userId, endpoint, p256dh, auth));
    }

    /** Only the owner of the subscription may drop it. */
    public void unsubscribe(String userId, String endpoint) {
        subscriptions.findByEndpoint(endpoint)
                .filter(s -> s.userId().equals(userId))
                .ifPresent(s -> subscriptions.deleteByEndpoint(endpoint));
    }

    /**
     * Registers an app installation.
     *
     * <p>Called again every time the app starts with a token in hand, which is what makes it
     * self-healing: an installation whose token was rotated re-registers under the same token row,
     * and one that belongs to another account changes owner.
     */
    public void registerDevice(String userId, String token, PushDevicePlatform platform) {
        String id = devices.findByToken(token).map(PushDevice::idDevice).orElseGet(identifiers::generate);
        devices.save(new PushDevice(id, userId, token, platform));
    }

    /** Only the owner of the installation may drop it, so one account cannot silence another. */
    public void unregisterDevice(String userId, String token) {
        devices.findByToken(token)
                .filter(d -> d.userId().equals(userId))
                .ifPresent(d -> devices.deleteByToken(token));
    }

    /**
     * Drops everything this user could have been reached at.
     *
     * <p>Both, not just the preferences: an account deleted must not leave a relay token behind
     * that keeps sending alerts to a phone whose owner no longer exists.
     */
    public void forget(String userId) {
        preferences.forget(userId);
        devices.deleteByUser(userId);
    }
}