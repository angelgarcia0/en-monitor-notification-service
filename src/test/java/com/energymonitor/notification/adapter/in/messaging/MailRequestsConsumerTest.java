package com.energymonitor.notification.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.SendEmailVerification;
import com.energymonitor.notification.application.usecase.SendPasswordResetEmail;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Mail requests consumer")
class MailRequestsConsumerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-10-20T15:15:00Z");

    private final SendEmailVerification verification = mock(SendEmailVerification.class);
    private final SendPasswordResetEmail passwordReset = mock(SendPasswordResetEmail.class);
    private final NotificationRecordPort records = mock(NotificationRecordPort.class);
    private final EmailSender.PreparedDelivery pending = mock(EmailSender.PreparedDelivery.class);
    private final MailRequestsConsumer consumer = new MailRequestsConsumer(verification,
            passwordReset, records, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("mails a verification code under the message id")
    void mailsVerificationCode() {
        given(records.findById("msg0000001")).willReturn(Optional.empty());
        given(verification.openForDelivery("msg0000001", "use0000009", "new@example.com",
                "123456", VALID_UNTIL)).willReturn(pending);

        consumer.on(message("msg0000001", "mail.email-verification",
                "{\"userId\":\"use0000009\",\"recipient\":\"new@example.com\",\"code\":\"123456\","
                        + "\"validUntil\":\"2026-10-20T15:15:00Z\"}"));

        verify(verification).send(pending);
    }

    @Test
    @DisplayName("mails a recovery code with its token as the source of the record")
    void mailsRecoveryCode() {
        given(records.findById("msg0000002")).willReturn(Optional.empty());
        given(passwordReset.openForDelivery("msg0000002", "use0000009", null, "rst0000001",
                "new@example.com", null, "654321", VALID_UNTIL)).willReturn(pending);

        consumer.on(message("msg0000002", "mail.password-reset",
                "{\"userId\":\"use0000009\",\"recipient\":\"new@example.com\","
                        + "\"idResetToken\":\"rst0000001\",\"code\":\"654321\","
                        + "\"validUntil\":\"2026-10-20T15:15:00Z\"}"));

        verify(passwordReset).send(pending);
    }

    @Test
    @DisplayName("drops a request whose delivery record already exists")
    void dropsDuplicate() {
        given(records.findById("msg0000003")).willReturn(Optional.of(mock(Notification.class)));

        consumer.on(message("msg0000003", "mail.email-verification", "{}"));

        verifyNoInteractions(verification, passwordReset);
    }

    @Test
    @DisplayName("drops a request that lost the race against another copy")
    void dropsConcurrentCopy() {
        given(records.findById("msg0000004")).willReturn(Optional.empty());
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(verification).openForDelivery(any(), any(), any(), any(), any());

        consumer.on(message("msg0000004", "mail.email-verification",
                "{\"userId\":\"use0000009\",\"recipient\":\"new@example.com\",\"code\":\"123456\","
                        + "\"validUntil\":\"2026-10-20T15:15:00Z\"}"));

        verify(verification, org.mockito.Mockito.never()).send(any());
    }

    @Test
    @DisplayName("drops a request whose code has already expired")
    void dropsExpired() {
        given(records.findById("msg0000005")).willReturn(Optional.empty());

        consumer.on(message("msg0000005", "mail.email-verification",
                "{\"userId\":\"use0000009\",\"recipient\":\"new@example.com\",\"code\":\"123456\","
                        + "\"validUntil\":\"2026-10-20T14:59:59Z\"}"));

        verifyNoInteractions(verification);
    }

    @Test
    @DisplayName("rejects a request without a message id, which could not be deduplicated")
    void rejectsMissingId() {
        assertThatThrownBy(() -> consumer.on(message(null, "mail.email-verification", "{}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static Message message(String id, String type, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setType(type);
        properties.setMessageId(id);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
