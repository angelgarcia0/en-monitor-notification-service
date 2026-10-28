package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.NotificationPreference;
import java.util.Optional;

public interface NotificationPreferencePort {

    Optional<NotificationPreference> find(String userId);

    void save(NotificationPreference preference);

    /** Removes the preferences and every push subscription of a deleted account. */
    void forget(String userId);
}
