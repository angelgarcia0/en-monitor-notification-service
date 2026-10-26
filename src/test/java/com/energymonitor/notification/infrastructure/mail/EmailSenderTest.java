package com.energymonitor.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationStatus;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("EmailSender")
class EmailSenderTest {

    private static final String ID = "ntf0a1b2c3";
    private static final String RECIPIENT = "ada@example.com";

    private static final String USER = "use0000001";
    private static final String SOURCE = "rst0000001";
    private static final String KEY = "security.password_reset.v1";

    /** One message, in the shape the gateway now accepts. */
    private static EmailSender.Delivery delivery() {
        return new EmailSender.Delivery(ID, USER, null, NotificationSourceType.SECURITY, SOURCE, KEY,
                RECIPIENT, "subject", "text", "<p>html</p>");
    }

    private JavaMailSender mailSender;
    private RecordingNotificationLog log;
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        log = new RecordingNotificationLog();
        emailSender = new EmailSender(mailSender, log, "Energy Monitor <no-reply@example.com>");
    }

    @Test
    @DisplayName("records the attempt before the message leaves, not after")
    void recordsTheAttemptFirst() {
        List<String> order = new ArrayList<>();
        log.onOpen = () -> order.add("open");
        org.mockito.Mockito.doAnswer(invocation -> {
            order.add("send");
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        emailSender.send(delivery());

        // A message that was sent but never recorded cannot be diagnosed afterwards, and an
        // attempt that kills the process must still leave evidence that it happened.
        assertThat(order).containsExactly("open", "send");
    }

    @Test
    @DisplayName("closes the log as sent when the gateway accepts it")
    void closesTheLogAsSent() {
        emailSender.send(delivery());

        assertThat(log.sent).hasSize(1);
        assertThat(log.failed).isEmpty();
        assertThat(log.deliveryStatus).isEqualTo(NotificationStatus.SENT);
        assertThat(log.sentAt).isNotNull();
    }

    @Test
    @DisplayName("closes the log as failed when the gateway refuses")
    void closesTheLogAsFailed() {
        doThrow(new MailSendException("relay refused")).when(mailSender).send(any(MimeMessage.class));

        emailSender.send(delivery());

        assertThat(log.failed).hasSize(1);
        assertThat(log.sent).isEmpty();
        assertThat(log.deliveryStatus).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.reason).contains("relay refused");
    }

    @Test
    @DisplayName("swallows the failure, so a mail outage cannot enumerate accounts")
    void swallowsTheFailure() {
        doThrow(new MailSendException("relay refused")).when(mailSender).send(any(MimeMessage.class));

        // The endpoint answers 202 whether or not the account exists. An exception escaping here
        // would make exactly the registered addresses answer differently.
        emailSender.send(delivery());

        assertThat(log.failed).hasSize(1);
    }

    @Test
    @DisplayName("records a failure when no sender address is configured")
    void recordsAFailureWithNoSender() {
        EmailSender unconfigured = new EmailSender(mailSender, log, "  ");

        unconfigured.send(delivery());

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(log.deliveryStatus).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.reason).contains("app.mail.from");
    }

    @Test
    @DisplayName("still records the failure when the reason is missing from the exception")
    void recordsAFailureWithNoReason() {
        doThrow(new MailSendException("")).when(mailSender).send(any(MimeMessage.class));

        emailSender.send(delivery());

        assertThat(log.deliveryStatus).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("carries the recovery code in the body, never in the log")
    void carriesTheCodeInTheBodyOnly() {
        emailSender.send(new EmailSender.Delivery(ID, USER, null, NotificationSourceType.SECURITY,
                SOURCE, KEY, RECIPIENT, "subject", "code: 123456",
                "<p>123456</p>"));

        // The log names the account, the context, the row and the wording key, and nothing else.
        // No address, no subject, and above all no payload: anyone able to read the log must not be
        // able to take over the account.
        assertThat(log.openedFor).containsExactly(
                new String[] {USER, null, NotificationSourceType.SECURITY.name(), SOURCE, KEY,
                        NotificationChannel.EMAIL.name()});
        assertThat(String.join("", log.openedFor.getFirst())).doesNotContain("123456");
    }

    @Test
    @DisplayName("escapes a value that came from a user before it reaches HTML")
    void escapesUntrustedHtml() {
        assertThat(EmailSender.escapeHtml("<script>alert(1)</script>"))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("escapes a null value rather than throwing while rendering a mail")
    void escapesNull() {
        assertThat(EmailSender.escapeHtml(null)).isEmpty();
    }

    /** Stands in for the persistence adapter and remembers what it was told. */
    private static final class RecordingNotificationLog implements NotificationRecordPort {

        private final List<String[]> openedFor = new ArrayList<>();
        private final List<String> sent = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private NotificationStatus deliveryStatus;
        private String reason;
        private LocalDateTime sentAt;
        private Runnable onOpen = () -> {
        };

        @Override
        public Notification openForSending(String idNotification, String userId, String homeId,
                                           NotificationSourceType type, String sourceId,
                                           String messageKey, NotificationChannel channel) {
            onOpen.run();
            openedFor.add(new String[] {userId, homeId, type.name(), sourceId, messageKey,
                    channel.name()});
            deliveryStatus = NotificationStatus.PENDING;
            return new Notification(idNotification, userId, homeId, channel, type, sourceId,
                    messageKey, NotificationStatus.PENDING, null, null, null, null);
        }

        @Override
        public List<Notification> listPendingOpenedBefore(LocalDateTime openedBefore, int limit) {
            return List.of();
        }

        @Override
        public int failAbandoned(LocalDateTime openedBefore, String reason) {
            return 0;
        }

        @Override
        public void recordSent(String idNotification, LocalDateTime at) {
            sent.add(idNotification);
            deliveryStatus = NotificationStatus.SENT;
            sentAt = at;
        }

        @Override
        public void recordFailure(String idNotification, String why) {
            failed.add(idNotification);
            deliveryStatus = NotificationStatus.FAILED;
            reason = why;
        }

        @Override
        public Optional<Notification> findById(String idNotification) {
            return Optional.empty();
        }
    }
}
