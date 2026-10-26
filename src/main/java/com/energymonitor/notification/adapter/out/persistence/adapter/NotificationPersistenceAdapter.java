package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import com.energymonitor.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.energymonitor.notification.adapter.out.persistence.repository.NotificationRepository;
import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationStatus;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the delivery log.
 *
 * <p><strong>Every method opens its own transaction</strong> ({@link Propagation#REQUIRES_NEW}).
 * That is a requirement of the flow this log serves, not a default. The row is written before
 * the message is attempted and closed after, and those two moments can end up in different
 * transactions: the opening call comes from the request that triggered the recovery, the closing
 * call from the thread that performs the delivery. Joining an ambient transaction would mean the
 * entry only becomes visible once that other transaction commits, so a crash mid-delivery would
 * leave no trace that a message had ever been attempted.
 */
@Component
public class NotificationPersistenceAdapter implements NotificationRecordPort {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public NotificationPersistenceAdapter(NotificationRepository repository,
                                           NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification openForSending(String idNotification, String userId, String homeId,
                                       NotificationSourceType type, String sourceId,
                                       String messageKey, NotificationChannel channel) {
        Notification pending = Notification.pending(idNotification, userId, homeId, channel, type,
                sourceId, messageKey);
        return mapper.toDomain(repository.save(mapper.toEntity(pending)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(String idNotification, LocalDateTime sentAt) {
        repository.findById(idNotification).ifPresent(entity ->
                entity.settled(null, sentAt, NotificationStatus.SENT));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String idNotification, String reason) {
        String recorded = Notification.failureReason(reason);
        repository.findById(idNotification)
                .ifPresent(entity -> entity.settled(recorded, null, NotificationStatus.FAILED));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> listPendingOpenedBefore(LocalDateTime openedBefore, int limit) {
        return repository
                .findByDeliveryStatusAndCreatedAtLessThanOrderByCreatedAtAsc(NotificationStatus.PENDING,
                        openedBefore, org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failAbandoned(LocalDateTime openedBefore, String reason) {
        String recorded = Notification.failureReason(reason);
        return repository.failAbandoned(NotificationStatus.PENDING, openedBefore, recorded,
                NotificationStatus.FAILED);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(String idNotification) {
        return repository.findById(idNotification).map(mapper::toDomain);
    }
}
