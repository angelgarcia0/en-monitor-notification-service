package com.energymonitor.notification.adapter.out.persistence.mapper;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import com.energymonitor.notification.domain.model.Notification;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Translates between the delivery log as the domain sees it and as it is stored.
 *
 * <p>Kept as a component rather than static methods so the persistence adapter stays the only
 * place that knows both shapes. The values an in-app activity notification is worded with are
 * stored as a JSON object of strings; a notification without them stores nothing.
 */
@Component
public class NotificationMapper {

    private static final TypeReference<Map<String, String>> PARAMS = new TypeReference<>() {
    };

    private final ObjectMapper json;

    public NotificationMapper(ObjectMapper json) {
        this.json = json;
    }

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
                readParams(entity.messageParams()),
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
                writeParams(notification.messageParams()),
                notification.deliveryStatus(),
                notification.sentAt(),
                notification.readAt(),
                notification.failureReason()
        );
    }

    private Map<String, String> readParams(String stored) {
        return stored == null || stored.isBlank() ? Map.of() : json.readValue(stored, PARAMS);
    }

    private String writeParams(Map<String, String> params) {
        return params.isEmpty() ? null : json.writeValueAsString(params);
    }
}
