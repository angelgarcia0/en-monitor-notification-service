package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.energymonitor.notification.adapter.out.persistence.repository.NotificationRepository;
import com.energymonitor.notification.application.port.out.ActivityNotificationPort;
import com.energymonitor.notification.domain.model.Notification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the in-app activity notifications in the {@code notification} table.
 *
 * <p>Each one in its own transaction, like the rest of the log: a message for one member is
 * kept even when writing the next one fails.
 */
@Component
public class ActivityNotificationPersistenceAdapter implements ActivityNotificationPort {

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
}
