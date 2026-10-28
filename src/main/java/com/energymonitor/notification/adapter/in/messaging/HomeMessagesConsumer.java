package com.energymonitor.notification.adapter.in.messaging;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.NotifyAlert;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.HomeMessagesMessagingConfiguration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivers the alerts and recommendations energy-monitor-backend announces to the members of their
 * home, by mail and push.
 *
 * <p>On the listener thread, not handed to another one: the message is acknowledged only once the
 * delivery ran, so a home service that does not answer makes the broker retry it instead of losing
 * it. A failure for one member is logged and does not stop the others ({@link NotifyAlert}).
 *
 * <p>The broker may deliver an event twice. When the delivery log already holds a row about that
 * alert or recommendation, the copy is dropped, so no member is mailed or pushed twice.
 *
 * <p>Only with {@code messaging.home-messages.enabled}, like its queues: see
 * {@link HomeMessagesMessagingConfiguration}.
 */
@Component
@ConditionalOnProperty(name = "messaging.home-messages.enabled", havingValue = "true")
public class HomeMessagesConsumer {

    static final String ALERT_RAISED = "alert.raised";
    static final String RECOMMENDATION_CREATED = "recommendation.created";

    private static final Logger LOG = LoggerFactory.getLogger(HomeMessagesConsumer.class);

    private final NotifyAlert notifyAlert;
    private final NotificationRecordPort records;
    private final ObjectMapper json;

    public HomeMessagesConsumer(NotifyAlert notifyAlert, NotificationRecordPort records,
                                ObjectMapper json) {
        this.notifyAlert = notifyAlert;
        this.records = records;
        this.json = json;
    }

    @RabbitListener(queues = HomeMessagesMessagingConfiguration.ALERT_RAISED_QUEUE)
    public void onAlert(Message message) {
        requireType(message, ALERT_RAISED);
        var event = json.readValue(message.getBody(), AlertRaisedMessage.class);
        deliver(NotificationSourceType.ALERT, event.idAlert(), event.homeId(), event.deviceId(),
                event.messageKey());
    }

    @RabbitListener(queues = HomeMessagesMessagingConfiguration.RECOMMENDATION_CREATED_QUEUE)
    public void onRecommendation(Message message) {
        requireType(message, RECOMMENDATION_CREATED);
        var event = json.readValue(message.getBody(), RecommendationCreatedMessage.class);
        deliver(NotificationSourceType.RECOMMENDATION, event.idRecommendation(), event.homeId(),
                event.deviceId(), event.messageKey());
    }

    private void deliver(NotificationSourceType source, String sourceId, String homeId,
                         String deviceId, String messageKey) {
        if (isBlank(sourceId) || isBlank(homeId) || isBlank(messageKey)) {
            throw new AmqpRejectAndDontRequeueException(
                    source + " message without its identifier, home or message key");
        }
        if (records.anyAbout(source, sourceId)) {
            LOG.info("{} {} already delivered, dropped", source, sourceId);
            return;
        }
        notifyAlert.notify(source, sourceId, homeId, deviceId, messageKey);
    }

    private static void requireType(Message message, String expected) {
        String type = message.getMessageProperties().getType();
        if (!expected.equals(type)) {
            throw new AmqpRejectAndDontRequeueException("unexpected message type " + type);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Body of {@code alert.raised}.
     *
     * @param deviceId null when the alert is about the whole home
     */
    record AlertRaisedMessage(String idAlert, String homeId, String deviceId, String type,
                              String messageKey, Instant dateTime) {
    }

    /**
     * Body of {@code recommendation.created}.
     *
     * @param deviceId null when the recommendation is about the whole home
     */
    record RecommendationCreatedMessage(String idRecommendation, String homeId, String deviceId,
                                        String type, String messageKey, Instant dateTime) {
    }
}
