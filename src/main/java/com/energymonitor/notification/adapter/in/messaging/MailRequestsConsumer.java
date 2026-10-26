package com.energymonitor.notification.adapter.in.messaging;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.application.usecase.SendEmailVerification;
import com.energymonitor.notification.application.usecase.SendPasswordResetEmail;
import com.energymonitor.notification.infrastructure.NotificationMessagingConfiguration;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Mails the verification and recovery codes the security service hands over.
 *
 * <p>The message id becomes the id of the delivery record. A message delivered twice therefore
 * finds its record and is dropped, and two copies racing each other collide on the primary key,
 * so a code is never mailed twice. A message whose code has already expired is dropped too.
 *
 * <p>The code is never logged; only the delivery record, without the code, stays behind.
 *
 * <p>Only with {@code messaging.security-events.enabled}, like its queue: see
 * {@link NotificationMessagingConfiguration}.
 */
@Component
@ConditionalOnProperty(name = "messaging.security-events.enabled", havingValue = "true")
public class MailRequestsConsumer {

    static final String EMAIL_VERIFICATION = "mail.email-verification";
    static final String PASSWORD_RESET = "mail.password-reset";

    private static final Logger LOG = LoggerFactory.getLogger(MailRequestsConsumer.class);

    private final SendEmailVerification sendEmailVerification;
    private final SendPasswordResetEmail sendPasswordResetEmail;
    private final NotificationRecordPort records;
    private final ObjectMapper json;
    private final Clock clock;

    public MailRequestsConsumer(SendEmailVerification sendEmailVerification,
                                SendPasswordResetEmail sendPasswordResetEmail,
                                NotificationRecordPort records, ObjectMapper json, Clock clock) {
        this.sendEmailVerification = sendEmailVerification;
        this.sendPasswordResetEmail = sendPasswordResetEmail;
        this.records = records;
        this.json = json;
        this.clock = clock;
    }

    @RabbitListener(queues = NotificationMessagingConfiguration.MAIL_QUEUE)
    public void on(Message message) {
        String id = message.getMessageProperties().getMessageId();
        String type = message.getMessageProperties().getType();
        if (id == null || id.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("mail request without a message id");
        }
        if (records.findById(id).isPresent()) {
            LOG.info("Mail request {} already delivered, dropped", id);
            return;
        }
        byte[] body = message.getBody();
        try {
            if (EMAIL_VERIFICATION.equals(type)) {
                var request = json.readValue(body, EmailVerificationMessage.class);
                if (expired(id, request.validUntil())) {
                    return;
                }
                send(sendEmailVerification.openForDelivery(id, request.userId(),
                        request.recipient(), request.code(), request.validUntil()), false);
            } else if (PASSWORD_RESET.equals(type)) {
                var request = json.readValue(body, PasswordResetMessage.class);
                if (expired(id, request.validUntil())) {
                    return;
                }
                send(sendPasswordResetEmail.openForDelivery(id, request.userId(), null,
                        request.idResetToken(), request.recipient(), null, request.code(),
                        request.validUntil()), true);
            } else {
                throw new AmqpRejectAndDontRequeueException("unexpected message type " + type);
            }
        } catch (DataIntegrityViolationException duplicate) {
            LOG.info("Mail request {} is being delivered by another copy, dropped", id);
        }
    }

    private boolean expired(String id, Instant validUntil) {
        if (validUntil.isAfter(clock.instant())) {
            return false;
        }
        LOG.info("Mail request {} arrived after its code expired, dropped", id);
        return true;
    }

    private void send(EmailSender.PreparedDelivery pending, boolean passwordReset) {
        if (passwordReset) {
            sendPasswordResetEmail.send(pending);
        } else {
            sendEmailVerification.send(pending);
        }
    }

    /** Body of {@code mail.email-verification}. */
    record EmailVerificationMessage(String userId, String recipient, String code,
                                    Instant validUntil) {
    }

    /** Body of {@code mail.password-reset}. */
    record PasswordResetMessage(String userId, String recipient, String idResetToken, String code,
                                Instant validUntil) {
    }
}
