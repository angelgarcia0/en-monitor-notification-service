package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import com.energymonitor.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.energymonitor.notification.adapter.out.persistence.repository.NotificationRepository;
import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the in-app activity notifications in the {@code notification} table.
 *
 * <p>Each one is written in its own transaction, like the rest of the log: a message for one
 * member is kept even when writing the next one fails.
 */
@Component
public class ActivityNotificationPersistenceAdapter implements ActivityNotificationPort {

    /** The sources of the activity notifications; every lookup is restricted to them. */
    static final Set<NotificationSourceType> SOURCES = Set.of(NotificationSourceType.ACCOUNT,
            NotificationSourceType.HOME, NotificationSourceType.DEVICE);

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public ActivityNotificationPersistenceAdapter(NotificationRepository repository,
                                                  NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Notification notification) {
        repository.save(mapper.toEntity(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> listForUser(String userId, String homeId, int limit) {
        return repository.findInApp(userId, NotificationChannel.IN_APP, SOURCES, homeId,
                        PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findForUser(String userId, String idNotification) {
        return active(idNotification)
                .filter(entity -> entity.userId().equals(userId))
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markRead(String idNotification, LocalDateTime readAt) {
        active(idNotification).ifPresent(entity -> entity.read(readAt));
    }

    @Override
    @Transactional
    public int markAllRead(String userId, String homeId, LocalDateTime readAt) {
        return repository.markInAppRead(userId, NotificationChannel.IN_APP, SOURCES, homeId, readAt);
    }

    @Override
    @Transactional
    public void delete(String idNotification, LocalDateTime deletedAt) {
        active(idNotification).ifPresent(entity -> entity.setDeletedAt(deletedAt));
    }

    private Optional<NotificationEntity> active(String idNotification) {
        return repository.findById(idNotification)
                .filter(entity -> entity.getDeletedAt() == null)
                .filter(entity -> entity.channel() == NotificationChannel.IN_APP
                        && SOURCES.contains(entity.sourceType()));
    }
}
