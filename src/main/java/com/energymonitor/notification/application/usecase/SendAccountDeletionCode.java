package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.mail.EmailSender;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Renders the message with the code that confirms deleting an account created with Google, and
 * hands it to the mail gateway.
 *
 * <p>Same split as {@link SendEmailVerification}: wording here, transport in {@link EmailSender}.
 * The code is never stored; the delivery log records only {@link #MESSAGE_KEY}. The wording says
 * plainly that the code deletes the account, so nobody types it believing it verifies something.
 */
@Component
public class SendAccountDeletionCode {

    private static final String SUBJECT = "Confirma la eliminación de tu cuenta";

    /** Stable name of this wording in the delivery log. */
    public static final String MESSAGE_KEY = "security.account_deletion.v1";

    private final EmailSender mailer;

    public SendAccountDeletionCode(EmailSender mailer) {
        this.mailer = mailer;
    }

    /**
     * Opens the delivery record and returns the rendered message, ready to be sent later.
     *
     * @param idNotification identifier of the delivery record
     * @param userId         the account to delete
     * @param recipient      its address
     * @param clearCode      the six-digit code
     * @param validUntil     the latest instant the code is accepted
     * @return the message, already recorded as pending
     */
    public EmailSender.PreparedDelivery openForDelivery(String idNotification, String userId,
                                                        String recipient, String clearCode,
                                                        Instant validUntil) {
        // Rounded down, like the verification mail: the code is accepted for at least this long.
        long minutes = Math.max(1L, Duration.between(Instant.now(), validUntil).toMinutes());
        String text = """
                Tu código para eliminar tu cuenta de Energy Monitor es: %s

                Vence en %d minutos. Al escribirlo, tu cuenta y tus datos se eliminan.
                Si no pediste eliminar tu cuenta, ignora este correo: tu cuenta sigue igual.
                """.formatted(clearCode, minutes);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px">
                  <p>Escribe este código para <b>eliminar tu cuenta</b> de Energy Monitor:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center">%s</p>
                  <p style="color:#555">Vence en <b>%d minutos</b>. Al escribirlo, tu cuenta y tus datos se eliminan.</p>
                  <p style="color:#888;font-size:13px">Si no pediste eliminar tu cuenta, ignora este correo: tu cuenta sigue igual.</p>
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
}
