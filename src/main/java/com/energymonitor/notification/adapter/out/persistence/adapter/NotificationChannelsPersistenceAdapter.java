package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationPreferenceEntity;
import com.energymonitor.notification.adapter.out.persistence.entity.PushSubscriptionEntity;
import com.energymonitor.notification.adapter.out.persistence.repository.NotificationPreferenceRepository;
import com.energymonitor.notification.adapter.out.persistence.repository.PushSubscriptionRepository;
import com.energymonitor.notification.application.port.out.NotificationPreferencePort;
import com.energymonitor.notification.application.port.out.PushSubscriptionPort;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preferences and push subscriptions: what a user wants and which browsers accepted to receive it.
 *
 * <p>App installations are deliberately not here. {@link PushDevicePersistenceAdapter} holds those,
 * because a relay token and a browser subscription are different shapes rather than two kinds of
 * the same one, and folding them into one adapter would only make the differences harder to see.
 * The two are cleaned up together from {@code ManageNotificationChannels#forget}.
 */
@Component
public class NotificationChannelsPersistenceAdapter implements NotificationPreferencePort, PushSubscriptionPort {

    private final NotificationPreferenceRepository preferences;
    private final PushSubscriptionRepository subscriptions;

    public NotificationChannelsPersistenceAdapter(NotificationPreferenceRepository preferences,
                                                  PushSubscriptionRepository subscriptions) {
        this.preferences = preferences;
        this.subscriptions = subscriptions;
    }

    @Override
    public Optional<NotificationPreference> find(String userId) {
        return preferences.findById(userId)
                .map(e -> new NotificationPreference(e.getUserId(), e.isEmailEnabled(), e.isPushEnabled()));
    }

    @Override
    @Transactional
    public void save(NotificationPreference preference) {
        NotificationPreferenceEntity entity = preferences.findById(preference.userId())
                .orElseGet(() -> new NotificationPreferenceEntity(preference.userId(), true, true));
        entity.setEmailEnabled(preference.emailEnabled());
        entity.setPushEnabled(preference.pushEnabled());
        preferences.save(entity);
    }

    @Override
    public List<PushSubscription> findByUser(String userId) {
        return subscriptions.findByUserId(userId).stream().map(NotificationChannelsPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<PushSubscription> findByEndpoint(String endpoint) {
        return subscriptions.findByEndpoint(endpoint).map(NotificationChannelsPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(PushSubscription subscription) {
        // The same browser subscribing again (new keys, or another user on that browser)
        // replaces the row: one endpoint, one owner.
        PushSubscriptionEntity entity = subscriptions.findByEndpoint(subscription.endpoint())
                .orElseGet(() -> new PushSubscriptionEntity(subscription.idSubscription(), subscription.userId(),
                        subscription.endpoint(), subscription.p256dh(), subscription.auth()));
        entity.setUserId(subscription.userId());
        entity.setP256dh(subscription.p256dh());
        entity.setAuth(subscription.auth());
        subscriptions.save(entity);
    }

    @Override
    @Transactional
    public void deleteByEndpoint(String endpoint) {
        subscriptions.deleteByEndpoint(endpoint);
    }

    @Override
    @Transactional
    public void forget(String userId) {
        subscriptions.deleteByUserId(userId);
        preferences.deleteById(userId);
    }

    private static PushSubscription toDomain(PushSubscriptionEntity e) {
        return new PushSubscription(e.getIdSubscription(), e.getUserId(), e.getEndpoint(), e.getP256dh(), e.getAuth());
    }
}