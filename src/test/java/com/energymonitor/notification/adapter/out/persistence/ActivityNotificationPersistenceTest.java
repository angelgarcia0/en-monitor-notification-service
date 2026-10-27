package com.energymonitor.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.energymonitor.notification.adapter.out.persistence.adapter.ActivityNotificationPersistenceAdapter;
import com.energymonitor.notification.adapter.out.persistence.adapter.NotificationPersistenceAdapter;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.NotificationStatus;
import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Round trips of the in-app activity notifications against the real schema. Not
 * {@code @Transactional}: the adapter commits in a transaction of its own, so each test removes
 * the rows it created.
 */
@SpringBootTest
@DisplayName("Activity notifications in the log")
class ActivityNotificationPersistenceTest extends JwtKeyedTest {

    private static final String ID = "act0000001";
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    @Autowired
    private ActivityNotificationPersistenceAdapter activity;
    @Autowired
    private NotificationPersistenceAdapter notifications;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactions;

    @AfterEach
    void removeTheRow() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> entityManager
                .createNativeQuery("DELETE FROM notification WHERE id_notification = ?1")
                .setParameter(1, ID).executeUpdate());
    }

    @Test
    @DisplayName("keeps the names the apps word the message with")
    void keepsTheMessageParams() {
        Notification joined = Notification.pendingInApp(ID, "use0000001", "hom0000001",
                NotificationSourceType.HOME, "msg0000001", "activity.home.member-joined.v1",
                Map.of("actorName", "Ana", "homeName", "Casa"));
        joined.markAsSent(SENT_AT);

        activity.save(joined);

        Notification read = notifications.findById(ID).orElseThrow();
        assertThat(read.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(read.sourceType()).isEqualTo(NotificationSourceType.HOME);
        assertThat(read.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(read.messageParams()).containsExactlyInAnyOrderEntriesOf(
                Map.of("actorName", "Ana", "homeName", "Casa"));
    }

    @Test
    @DisplayName("a name nobody knows is left out, and a message without names stores none")
    void leavesOutMissingNames() {
        HashMap<String, String> params = new HashMap<>();
        params.put("actorName", null);
        Notification welcome = Notification.pendingInApp(ID, "use0000001", null,
                NotificationSourceType.ACCOUNT, "msg0000002", "activity.account.welcome.v1", params);
        welcome.markAsSent(SENT_AT);

        activity.save(welcome);

        assertThat(notifications.findById(ID).orElseThrow().messageParams()).isEmpty();
        Object stored = new TransactionTemplate(transactions).execute(status -> entityManager
                .createNativeQuery("SELECT message_params FROM notification WHERE id_notification = ?1")
                .setParameter(1, ID).getSingleResult());
        assertThat(stored).isNull();
    }
}
