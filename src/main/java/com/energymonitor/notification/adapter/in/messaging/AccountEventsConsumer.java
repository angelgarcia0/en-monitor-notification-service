package com.energymonitor.notification.adapter.in.messaging;

import com.energymonitor.notification.application.port.in.AccountAdministered;
import com.energymonitor.notification.application.port.in.AccountRegistered;
import com.energymonitor.notification.application.usecase.AdminNotifications;
import com.energymonitor.notification.application.usecase.ManageNotificationChannels;
import com.energymonitor.notification.infrastructure.NotificationMessagingConfiguration;
import java.time.Instant;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns the account events of the security service into in-app notifications for the
 * administrators, and forgets where a deleted account was reached.
 *
 * <p>The broker may deliver an event twice; {@link AdminNotifications} writes it once, and
 * forgetting an account twice finds nothing the second time.
 *
 * <p>Only with {@code messaging.security-events.enabled}, like its queue: see
 * {@link NotificationMessagingConfiguration}.
 */
@Component
@ConditionalOnProperty(name = "messaging.security-events.enabled", havingValue = "true")
public class AccountEventsConsumer {

    static final String ACCOUNT_REGISTERED = "account.registered";
    static final String ACCOUNT_ADMINISTERED = "account.administered";
    static final String ACCOUNT_DELETED = "account.deleted";

    private final AdminNotifications adminNotifications;
    private final ManageNotificationChannels channels;
    private final ObjectMapper json;

    public AccountEventsConsumer(AdminNotifications adminNotifications,
                                 ManageNotificationChannels channels, ObjectMapper json) {
        this.adminNotifications = adminNotifications;
        this.channels = channels;
        this.json = json;
    }

    @RabbitListener(queues = NotificationMessagingConfiguration.ACCOUNT_EVENTS_QUEUE)
    public void on(Message message) {
        String type = message.getMessageProperties().getType();
        byte[] body = message.getBody();
        if (ACCOUNT_REGISTERED.equals(type)) {
            var event = json.readValue(body, AccountRegisteredMessage.class);
            adminNotifications.record(
                    new AccountRegistered(event.userId(), event.email(), event.occurredAt()));
        } else if (ACCOUNT_ADMINISTERED.equals(type)) {
            var event = json.readValue(body, AccountAdministeredMessage.class);
            adminNotifications.record(new AccountAdministered(event.actorId(), event.userId(),
                    AccountAdministered.Action.valueOf(event.action()), event.detail(),
                    event.occurredAt()));
        } else if (ACCOUNT_DELETED.equals(type)) {
            channels.forget(json.readValue(body, AccountDeletedMessage.class).userId());
        } else {
            throw new AmqpRejectAndDontRequeueException("unexpected message type " + type);
        }
    }

    /** Body of {@code account.registered}. */
    record AccountRegisteredMessage(String userId, String email, Instant occurredAt) {
    }

    /** Body of {@code account.deleted}. */
    record AccountDeletedMessage(String userId, Instant deletedAt) {
    }

    /** Body of {@code account.administered}. */
    record AccountAdministeredMessage(String actorId, String userId, String action, String detail,
                                      Instant occurredAt) {
    }
}
