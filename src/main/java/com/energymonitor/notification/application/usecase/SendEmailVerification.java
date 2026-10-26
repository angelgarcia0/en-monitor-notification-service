package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Renders the email-verification message and hands it to the mail gateway.
 *
 * <p>Same split as {@link SendPasswordResetEmail}: wording here, transport in {@link EmailSender}.
 * The code is never stored; the delivery log records only {@link #MESSAGE_KEY}.
 */
@Component
public class SendEmailVerification {

    private static final String SUBJECT = "Verifica tu correo";

    /** Stable name of this wording in the delivery log. */
    public static final String MESSAGE_KEY = "security.email_verification.v1";

    private final EmailSender mailer;

    public SendEmailVerification(EmailSender mailer) {
        this.mailer = mailer;
    }

    /**
     * Opens the delivery record and returns the rendered message, ready to be sent later.
     *
     * @param idNotification identifier of the delivery record
     * @param userId         the account being verified
     * @param recipient      the address the code confirms
     * @param clearCode      the six-digit code
     * @param validUntil     the latest instant the code is accepted
     * @return the message, already recorded as pending
     */
    public EmailSender.PreparedDelivery openForDelivery(String idNotification, String userId,
                                                        String recipient, String clearCode,
                                                        Instant validUntil) {
        // Rounded down, unlike the recovery mail: the code is accepted for at least this long,
        // so the mail never promises more time than there is.
        long minutes = Math.max(1L, Duration.between(Instant.now(), validUntil).toMinutes());
        String text = """
                Tu código para verificar tu correo es: %s

                Vence en %d minutos.
                Si no creaste una cuenta en Energy Monitor, ignora este correo.
                """.formatted(clearCode, minutes);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px">
                  <p>Escribe este código para verificar tu correo:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center">%s</p>
                  <p style="color:#555">Vence en <b>%d minutos</b>.</p>
                  <p style="color:#888;font-size:13px">Si no creaste una cuenta en Energy Monitor, ignora este correo.</p>
                </div>
                """.formatted(clearCode, minutes);
        return mailer.sendNow(new EmailSender.Delivery(idNotification, userId, null,
                NotificationSourceType.SECURITY, null, MESSAGE_KEY, recipient, SUBJECT, text,
                html));
    }

    /** Sends a message opened by {@link #openForDelivery}. */
    public void send(EmailSender.PreparedDelivery pending) {
        mailer.send(pending);
    }

    /** Closes a message that will not be attempted. */
    public void abandon(EmailSender.PreparedDelivery pending, String reason) {
        mailer.abandon(pending, reason);
    }
}
