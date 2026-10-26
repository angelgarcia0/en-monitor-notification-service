package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.PushDevice;
import java.util.List;
import java.util.Optional;

/**
 * App installations that receive push, kept apart from {@link PushSubscriptionPort} because a relay
 * token and a browser subscription are different shapes, not two kinds of the same one.
 */
public interface PushDevicePort {

    List<PushDevice> findByUser(String userId);

    Optional<PushDevice> findByToken(String token);

    void save(PushDevice device);

    /** Only the owner of the installation may drop it. */
    void deleteByToken(String token);

    /** Called when an account is deleted, so no token outlives it. */
    void deleteByUser(String userId);
}