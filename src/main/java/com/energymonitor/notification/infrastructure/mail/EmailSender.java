package com.energymonitor.notification.infrastructure.mail;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends a message over SMTP and records the outcome in the delivery log.
 *
 * <p><strong>The delivery log is written before the message leaves, not after.</strong> A row is
 * opened as {@code PENDING} first, so an attempt that kills the process still leaves evidence
 * that it happened. The log is the audit trail; a message that was sent without ever being
 * recorded cannot be diagnosed afterwards.
 *
 * <p><strong>Nothing here decides whether a message should be sent.</strong> There is no
 * permission check, no rate limit and no decision about whether the recipient exists. The caller
 * has already made that call and this class only carries it out, which is why it can be reused
 * unchanged for a verification code, an alert or a recommendation.
 *
 * <p><strong>No secret is ever logged.</strong> A password recovery code arrives here in the
 * body of the message; logging either the body or the code would hand anyone with read access to
 * the log the ability to take over the account, which is the exposure storing only a hash was
 * meant to prevent. What is recorded is the failure reason, which comes from the mail server
 * and describes the transport rather than the payload.
 */
@Service
public class EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final NotificationRecordPort notifications;
    private final String from;

    public EmailSender(JavaMailSender mailSender, NotificationRecordPort notifications,
                       @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.notifications = notifications;
        this.from = from;
    }

    /**
     * Sends a message and closes its delivery log entry.
     *
     * <p><strong>Never throws.</strong> The endpoint that triggers a recovery answers 202 whether
     * or not the account exists, so a transport failure escaping here would make exactly the
     * registered addresses answer differently and turn a mail outage into a way of enumerating
     * them. The failure is recorded in the log and swallowed.
     *
     * @param recipient destination address
     * @param type      what the message is about
     * @param subject   subject line
     * @param text      plain text body
     * @param html      HTML body, shown when the client supports it
     */
    public void send(Delivery delivery) {
        send(sendNow(delivery));
    }

    /**
     * Performs the delivery of an already-opened record and closes it.
     *
     * <p>Separated from {@link #sendNow} so the opening and the sending can happen in different
     * threads, which is what asynchronous delivery needs: the row is opened by the request and the
     * conversation with the mail server happens later, off it.
     *
     * <p><strong>Never throws.</strong> The endpoint that triggers a recovery answers 202 whether
     * or not the account exists, so a transport failure escaping here would make exactly the
     * registered addresses answer differently and turn a mail outage into a way of enumerating them.
     * The failure is recorded in the log and swallowed.
     *
     * @param pending the opened record and the bodies to send
     */
    public void send(PreparedDelivery pending) {
        try {
            deliver(pending.recipient(), pending.subject(), pending.text(), pending.html());
            notifications.recordSent(pending.idNotification(), java.time.LocalDateTime.now());
        } catch (MessagingException | RuntimeException failure) {
            LOG.warn("The notification {} could not be sent: {}", pending.idNotification(),
                    failure.getMessage());
            notifications.recordFailure(pending.idNotification(),
                    failure.getMessage() == null ? failure.getClass().getSimpleName()
                            : failure.getMessage());
        }
    }

    /**
     * Opens a record in {@code PENDING} and returns it with the bodies, without contacting anybody.
     *
     * <p>The row is written before the message leaves so that a delivery which is interrupted
     * halfway still leaves evidence. Doing it here rather than inside {@link #send} is what lets the
     * caller decide when the conversation with the mail server happens.
     *
     * @param recipient destination address
     * @param type      what the message is about
     * @param subject   subject line
     * @param text      plain text body
     * @param html      HTML body
     * @return the opened record and the bodies, ready to be sent later
     */
    public PreparedDelivery sendNow(Delivery delivery) {
        Notification attempt = notifications.openForSending(delivery.idNotification(),
                delivery.userId(), delivery.homeId(), delivery.sourceType(), delivery.sourceId(),
                delivery.messageKey(), NotificationChannel.EMAIL);
        return new PreparedDelivery(attempt.idNotification(), delivery.recipient(), delivery.subject(),
                delivery.text(), delivery.html());
    }

    /**
     * Closes an opened record as failed without attempting to send anything.
     *
     * <p>Used when the send cannot even be attempted, which today means the delivery queue refused
     * the work. The alternative, leaving the row {@code PENDING}, would claim a delivery that will
     * never happen for as long as the row exists.
     *
     * @param pending the record to close
     * @param reason  why it was abandoned, recorded verbatim up to the column width
     */
    public void abandon(PreparedDelivery pending, String reason) {
        LOG.warn("The notification {} was not attempted: {}", pending.idNotification(), reason);
        notifications.recordFailure(pending.idNotification(), reason);
    }

    private void deliver(String recipient, String subject, String text, String html)
            throws MessagingException {
        if (from == null || from.isBlank()) {
            // Failing here rather than sending from an empty sender, which no SMTP server accepts
            // and which would otherwise surface as an opaque rejection.
            throw new IllegalStateException("app.mail.from is not configured");
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper =
                new MimeMessageHelper(message, true, java.nio.charset.StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(text, html);
        mailSender.send(message);
    }

    /**
     * Escapes a value that came from a user before it is interpolated into HTML.
     *
     * @param value the untrusted value
     * @return the value safe to place in an HTML document
     */
    public static String escapeHtml(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    /**
     * A delivery whose record exists but whose message has not left yet.
     *
     * <p>Carries the rendered bodies so the thread that eventually sends them does not have to
     * render again, which would recompute a validity window and could describe a slightly different
     * deadline than the record it belongs to.
     *
     * @param idNotification identifier of the opened record
     * @param recipient      destination address
     * @param subject        subject line
     * @param text           plain text body
     * @param html           HTML body
     */
    public record PreparedDelivery(String idNotification, String recipient, String subject,
                                   String text, String html) {
    }

    /**
     * One message to be delivered, with everything the log needs and everything the body needs.
     *
     * <p>The log gets an identifier, the two references and a key; the transport gets the address
     * and the bodies. They travel together because a delivery cannot be recorded without both, and
     * splitting them across two parameters is how a body ends up in a place it should not be.
     *
     * @param idNotification identifier for the log entry, generated by the caller so it can refer
     *                       to the entry before the entry exists
     * @param userId        the account the message is for
     * @param homeId        the home it concerns, optional
     * @param sourceType    the context the message originates from
     * @param sourceId      the row in that context, optional
     * @param messageKey    stable name of the wording used, never the wording
     * @param recipient     destination address
     * @param subject       subject line
     * @param text          plain text body, which may carry a secret and is never stored
     * @param html          HTML body, same
     */
    public record Delivery(String idNotification, String userId, String homeId,
                           com.energymonitor.notification.domain.model.NotificationSourceType sourceType,
                           String sourceId, String messageKey, String recipient, String subject,
                           String text, String html) {
    }
}
