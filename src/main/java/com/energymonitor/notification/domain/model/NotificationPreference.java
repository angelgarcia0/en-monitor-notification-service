package com.energymonitor.notification.domain.model;

/**
 * Which channels a user wants alerts on. A user who never chose gets {@link #defaults}: both on,
 * because an alert nobody hears about is worse than one too many.
 *
 * <p>Push is also bounded by the browsers the user subscribed: with no subscription, nothing is
 * pushed whatever this says.
 */
public record NotificationPreference(String userId, boolean emailEnabled, boolean pushEnabled) {

    public NotificationPreference {
        Preconditions.text(userId, 10, "userId");
    }

    public static NotificationPreference defaults(String userId) {
        return new NotificationPreference(userId, true, true);
    }
}
