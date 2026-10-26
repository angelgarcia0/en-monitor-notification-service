package com.energymonitor.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.application.port.in.AccountAdministered;
import com.energymonitor.notification.application.port.in.AccountRegistered;
import com.energymonitor.notification.application.usecase.AdminNotifications;
import com.energymonitor.notification.application.usecase.ManageNotificationChannels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Account events consumer")
class AccountEventsConsumerTest {

    private static final Instant OCCURRED = Instant.parse("2026-10-20T15:00:00Z");

    private final AdminNotifications adminNotifications = mock(AdminNotifications.class);
    private final ManageNotificationChannels channels = mock(ManageNotificationChannels.class);
    private final AccountEventsConsumer consumer =
            new AccountEventsConsumer(adminNotifications, channels, JsonMapper.builder().build());

    @Test
    @DisplayName("a new account becomes an administrator notification")
    void recordsRegistration() {
        consumer.on(message("account.registered",
                "{\"userId\":\"use0000009\",\"email\":\"new@example.com\","
                        + "\"occurredAt\":\"2026-10-20T15:00:00Z\"}"));

        verify(adminNotifications).record(
                new AccountRegistered("use0000009", "new@example.com", OCCURRED));
    }

    @Test
    @DisplayName("an administrator's change keeps its action, detail and author")
    void recordsAdministration() {
        consumer.on(message("account.administered",
                "{\"actorId\":\"adm0000001\",\"userId\":\"use0000009\",\"action\":\"STATUS_CHANGED\","
                        + "\"detail\":\"BLOCKED\",\"occurredAt\":\"2026-10-20T15:00:00Z\"}"));

        verify(adminNotifications).record(new AccountAdministered("adm0000001", "use0000009",
                AccountAdministered.Action.STATUS_CHANGED, "BLOCKED", OCCURRED));
    }

    @Test
    @DisplayName("a deleted account is no longer reached by mail or push")
    void forgetsDeletedAccount() {
        consumer.on(message("account.deleted",
                "{\"userId\":\"use0000009\",\"deletedAt\":\"2026-10-20T15:00:00Z\"}"));

        verify(channels).forget("use0000009");
        verifyNoInteractions(adminNotifications);
    }

    @Test
    @DisplayName("an unknown type is rejected without retrying")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> consumer.on(message("account.renamed", "{}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(adminNotifications, channels);
    }

    static Message message(String type, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setType(type);
        properties.setMessageId("msg0000001");
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
