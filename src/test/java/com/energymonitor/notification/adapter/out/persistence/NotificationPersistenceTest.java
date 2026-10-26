package com.energymonitor.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.notification.adapter.out.persistence.adapter.NotificationPersistenceAdapter;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.NotificationStatus;
import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Round trips of the delivery log against the real schema.
 *
 * <p>Not {@code @Transactional}: every adapter method runs in its own {@code REQUIRES_NEW}
 * transaction and commits, so a test-managed transaction would neither isolate nor roll back
 * those writes. Each test removes the rows it created instead.
 */
@SpringBootTest
class NotificationPersistenceTest extends JwtKeyedTest {

    private static final String SENT_ID = "ntp0000001";
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 1, 15, 8, 0, 0);

    @Autowired
    private NotificationPersistenceAdapter notifications;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactions;

    @AfterEach
    void removeEveryRowItCreated() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> entityManager
                .createNativeQuery("DELETE FROM notification WHERE id_notification = ?1")
                .setParameter(1, SENT_ID).executeUpdate());
    }

    private void open(String idNotification) {
        notifications.openForSending(idNotification, "use0000001", null,
                NotificationSourceType.SECURITY, "rst0000001", "security.password_reset.v1",
                NotificationChannel.EMAIL);
    }

    private Notification reload(String idNotification) {
        return notifications.findById(idNotification).orElseThrow();
    }

    @Test
    @DisplayName("a recorded delivery is stored as SENT with its instant")
    void recordSentPersistsTheStatus() {
        open(SENT_ID);
        assertThat(reload(SENT_ID).deliveryStatus()).isEqualTo(NotificationStatus.PENDING);

        notifications.recordSent(SENT_ID, SENT_AT);

        Notification read = reload(SENT_ID);
        assertThat(read.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(read.sentAt()).isEqualTo(SENT_AT);
        assertThat(read.failureReason()).isNull();
    }

    @Test
    @DisplayName("a recorded failure is stored as FAILED with its reason")
    void recordFailurePersistsTheStatusAndReason() {
        open(SENT_ID);

        notifications.recordFailure(SENT_ID, "connection refused");

        Notification read = reload(SENT_ID);
        assertThat(read.deliveryStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(read.failureReason()).isEqualTo("connection refused");
        assertThat(read.sentAt()).isNull();
    }

    @Test
    @DisplayName("an oversized reason is stored cut to the column width")
    void oversizedReasonIsCutToTheColumn() {
        open(SENT_ID);

        notifications.recordFailure(SENT_ID, "x".repeat(500));

        Notification read = reload(SENT_ID);
        assertThat(read.deliveryStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(read.failureReason()).hasSize(200);
    }
}
