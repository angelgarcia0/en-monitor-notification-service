package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.infrastructure.mail.EmailSender;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import org.springframework.stereotype.Component;

/**
 * Renders the password recovery message and hands it to the mail gateway.
 *
 * <p>The wording and the layout of the message are decided here rather than in
 * {@link EmailSender}, which is a transport. That split is what lets the same gateway carry a
 * verification code or an alert later without being rewritten.
 *
 * <p>The recovery secret is passed in from the security service, which is the only holder of it
 * in clear text. This class never stores it, logs it or returns it.
 */
@Component
public class SendPasswordResetEmail {

    private static final String SUBJECT = "Recupera tu contraseña";

    /**
     * Stable name of the wording this service sends for a recovery code.
     *
     * <p>Stored instead of the wording, so the row explains which version went out without
     * containing it. Nothing about the code can be reconstructed from it.
     */
    public static final String MESSAGE_KEY = "security.password_reset.v1";

    private static final int VALID_MINUTES = 30;

    private final EmailSender mailer;

    public SendPasswordResetEmail(EmailSender mailer) {
        this.mailer = mailer;
    }

    /**
     * Sends a recovery code.
     *
     * @param recipient  destination address
     * @param firstName  the name the account gave, which may be null
     * @param clearCode  the recovery secret, in clear text
     * @param validUntil when the secret stops being redeemable
     */
    public void send(String idNotification, String userId, String homeId, String sourceId,
                     String recipient, String firstName, String clearCode,
                     java.time.Instant validUntil) {
        Rendered rendered = render(recipient, firstName, clearCode, validUntil);
        mailer.send(delivery(idNotification, userId, homeId, sourceId, recipient, rendered));
    }

    /**
     * Renders the message and opens its delivery record, without contacting the mail server.
     *
     * <p>The half of {@link #send} that has to happen on the request thread, because the record it
     * writes is the evidence a delivery leaves behind. The conversation with the mail server is the
     * other half, and it happens later.
     *
     * @param recipient  destination address
     * @param firstName  the name the account gave, may be null
     * @param clearCode  the recovery secret, in clear text
     * @param validUntil when the secret stops being redeemable
     * @return the opened record and the bodies, ready to be sent
     */
    public EmailSender.PreparedDelivery openForDelivery(String idNotification, String userId,
                                                        String homeId, String sourceId,
                                                        String recipient, String firstName,
                                                        String clearCode,
                                                        java.time.Instant validUntil) {
        Rendered rendered = render(recipient, firstName, clearCode, validUntil);
        return mailer.sendNow(
                delivery(idNotification, userId, homeId, sourceId, recipient, rendered));
    }

    /**
     * Assembles what the log and the transport each need from one message.
     *
     * <p>The source is always {@link NotificationSourceType#SECURITY}: this service sends on behalf
     * of the security service, and the row it refers to is the recovery token, identified by
     * {@code sourceId}.
     */
    private EmailSender.Delivery delivery(String idNotification, String userId, String homeId,
                                         String sourceId, String recipient, Rendered rendered) {
        return new EmailSender.Delivery(idNotification, userId, homeId,
                NotificationSourceType.SECURITY, sourceId, MESSAGE_KEY, recipient, SUBJECT,
                rendered.text(), rendered.html());
    }

    /**
     * Performs the delivery of an opened record.
     *
     * @param pending the record and bodies {@link #openForDelivery} produced
     */
    public void send(EmailSender.PreparedDelivery pending) {
        mailer.send(pending);
    }

    /**
     * Closes an opened record as failed without attempting the delivery.
     *
     * @param pending the record to close
     * @param reason  why it was abandoned
     */
    public void abandon(EmailSender.PreparedDelivery pending, String reason) {
        mailer.abandon(pending, reason);
    }

    /**
     * The rendered bodies of one recovery message.
     *
     * @param text plain text body
     * @param html HTML body
     */
    private record Rendered(String text, String html) {
    }

    private Rendered render(String recipient, String firstName, String clearCode,
                            java.time.Instant validUntil) {
        String name = firstName == null || firstName.isBlank() ? "" : firstName;
        // Rounded up, so the mail never promises less time than the token actually has: a
        // recipient who is told the code expires in 29 minutes and finds it valid for 30 has
        // been told a falsehood, however harmless it looks.
        long minutes = Math.max(1L,
                (long) Math.ceil(java.time.Duration.between(java.time.Instant.now(), validUntil)
                        .toMillis() / 60_000.0));

        // The greeting is omitted rather than left half-written. Interpolating an absent name
        // into a fixed "Hola %s," produces "Hola ,", which reads as a fault in the mail itself;
        // the recovery code below it is what the recipient actually needs.
        String textGreeting = name.isEmpty() ? "" : "Hola " + name + ",\n\n";

        String text = (textGreeting + """
                Tu código para recuperar la contraseña es: %s

                Vence en %d minutos y solo se puede usar una vez.
                Si no fuiste tú, ignora este correo.
                """).formatted(clearCode, minutes);

        // The name was typed by the user, so it is escaped before it reaches the HTML body:
        // unescaped it would let an account put markup into somebody else's mail client.
        String htmlGreeting = name.isEmpty() ? ""
                : "  <p>Hola " + EmailSender.escapeHtml(name) + ",</p>";

        String html = ("""
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px">
                %s
                  <p>Escribe este código en la pantalla de verificación:</p>
                  <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center">%s</p>
                  <p style="color:#555">Vence en <b>%d minutos</b> y solo se puede usar una vez.</p>
                  <p style="color:#888;font-size:13px">Si no fuiste tú, ignora este correo.</p>
                </div>
                """).formatted(htmlGreeting, clearCode, minutes);

        return new Rendered(text, html);
    }
}
