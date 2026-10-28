package com.energymonitor.notification.application.port.out;

import com.energymonitor.notification.domain.model.PushSubscription;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionPort {

    List<PushSubscription> findByUser(String userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void save(PushSubscription subscription);

    void deleteByEndpoint(String endpoint);
}
