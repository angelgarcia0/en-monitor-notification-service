package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import com.energymonitor.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.energymonitor.notification.adapter.out.persistence.repository.NotificationRepository;
import com.energymonitor.notification.application.port.out.AdminNotificationPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the in-app administrator notifications in the {@code notification} table.
 */
@Component
public class AdminNotificationPersistenceAdapter implements AdminNotificationPort {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public AdminNotificationPersistenceAdapter(NotificationRepository repository,
                                               NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Notification notification) {
        repository.save(mapper.toEntity(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String userId, String messageKey, String sourceId, LocalDateTime sentAt) {
        return repository.existsByUserIdAndChannelAndSourceTypeAndSourceIdAndMessageKeyAndSentAt(
                userId, NotificationChannel.IN_APP, NotificationSourceType.ADMINISTRATION,
                sourceId, messageKey, sentAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> listForUser(String userId, int limit) {
        return repository.findByUserIdAndChannelAndSourceTypeAndDeletedAtIsNullOrderBySentAtDesc(
                        userId, NotificationChannel.IN_APP, NotificationSourceType.ADMINISTRATION,
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
    public void delete(String idNotification, LocalDateTime deletedAt) {
        active(idNotification).ifPresent(entity -> entity.setDeletedAt(deletedAt));
    }

    private Optional<NotificationEntity> active(String idNotification) {
        return repository.findById(idNotification)
                .filter(entity -> entity.getDeletedAt() == null)
                .filter(entity -> entity.channel() == NotificationChannel.IN_APP
                        && entity.sourceType() == NotificationSourceType.ADMINISTRATION);
    }
}
