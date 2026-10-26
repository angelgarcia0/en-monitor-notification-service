package com.energymonitor.notification.adapter.out.persistence.mapper;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import com.energymonitor.notification.domain.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Translates between the delivery log as the domain sees it and as it is stored.
 *
 * <p>Kept as a component rather than static methods so the persistence adapter stays the only
 * place that knows both shapes.
 */
@Component
public class NotificationMapper {

    /**
     * @param entity stored row, may be null
     * @return the domain notification, or null when the row was null
     */
    public Notification toDomain(NotificationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Notification(
                entity.getIdNotification(),
                entity.userId(),
                entity.homeId(),
                entity.channel(),
                entity.sourceType(),
                entity.sourceId(),
                entity.messageKey(),
                entity.deliveryStatus(),
                entity.sentAt(),
                entity.readAt(),
                entity.failureReason(),
                entity.getDeletedAt()
        );
    }

    /**
     * @param notification domain notification, may be null
     * @return a new detached entity carrying the same state, or null when it was null
     */
    public NotificationEntity toEntity(Notification notification) {
        if (notification == null) {
            return null;
        }
        return new NotificationEntity(
                notification.idNotification(),
                notification.userId(),
                notification.homeId(),
                notification.channel(),
                notification.sourceType(),
                notification.sourceId(),
                notification.messageKey(),
                notification.deliveryStatus(),
                notification.sentAt(),
                notification.readAt(),
                notification.failureReason()
        );
    }
}
