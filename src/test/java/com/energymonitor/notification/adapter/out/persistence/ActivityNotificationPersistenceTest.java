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
 * the rows it created (identifiers starting with {@code act}, an account no other test uses).
 */
@SpringBootTest
@DisplayName("Activity notifications in the log")
class ActivityNotificationPersistenceTest extends JwtKeyedTest {

    private static final String USER = "actUser001";
    private static final String OTHER_USER = "actUser002";
    private static final String CASA = "actHome001";
    private static final String FINCA = "actHome002";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 27, 9, 0, 0);

    @Autowired
    private ActivityNotificationPersistenceAdapter activity;
    @Autowired
    private NotificationPersistenceAdapter notifications;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactions;

    @AfterEach
    void removeTheRows() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> entityManager
                .createNativeQuery("DELETE FROM notification WHERE id_notification LIKE 'act%'")
                .executeUpdate());
    }

    private Notification saved(String id, String userId, String homeId, NotificationSourceType source,
                               int minutesLater) {
        Notification notification = Notification.pendingInApp(id, userId, homeId, source,
                "msg0000001", "activity.home.member-joined.v1", Map.of("homeName", "Casa"));
        notification.markAsSent(AT.plusMinutes(minutesLater));
        activity.save(notification);
        return notification;
    }

    @Test
    @DisplayName("keeps the names the apps word the message with")
    void keepsTheMessageParams() {
        Notification joined = Notification.pendingInApp("act0000001", USER, CASA,
                NotificationSourceType.HOME, "msg0000001", "activity.home.member-joined.v1",
                Map.of("actorName", "Ana", "homeName", "Casa"));
        joined.markAsSent(AT);

        activity.save(joined);

        Notification read = notifications.findById("act0000001").orElseThrow();
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
        Notification welcome = Notification.pendingInApp("act0000001", USER, null,
                NotificationSourceType.ACCOUNT, "msg0000002", "activity.account.welcome.v1", params);
        welcome.markAsSent(AT);

        activity.save(welcome);

        assertThat(notifications.findById("act0000001").orElseThrow().messageParams()).isEmpty();
        Object stored = new TransactionTemplate(transactions).execute(status -> entityManager
                .createNativeQuery("SELECT message_params FROM notification WHERE id_notification = ?1")
                .setParameter(1, "act0000001").getSingleResult());
        assertThat(stored).isNull();
    }

    @Test
    @DisplayName("lists the caller's activity of every home, or of one, newest first")
    void listsByHome() {
        saved("act0000001", USER, CASA, NotificationSourceType.HOME, 0);
        saved("act0000002", USER, FINCA, NotificationSourceType.DEVICE, 1);
        saved("act0000003", USER, null, NotificationSourceType.ACCOUNT, 2);
        saved("act0000004", OTHER_USER, CASA, NotificationSourceType.HOME, 3);

        assertThat(activity.listForUser(USER, null, 50)).extracting(Notification::idNotification)
                .containsExactly("act0000003", "act0000002", "act0000001");
        assertThat(activity.listForUser(USER, CASA, 50)).extracting(Notification::idNotification)
                .containsExactly("act0000001");
    }

    @Test
    @DisplayName("leaves out the deleted ones and those that are not activity")
    void leavesOutDeletedAndOtherSources() {
        saved("act0000001", USER, CASA, NotificationSourceType.HOME, 0);
        saved("act0000002", USER, CASA, NotificationSourceType.HOME, 1);
        Notification admin = Notification.pending("act0000003", USER, null, NotificationChannel.IN_APP,
                NotificationSourceType.ADMINISTRATION, "use0000009", "admin.accountRegistered");
        admin.markAsSent(AT);
        activity.save(admin);

        activity.delete("act0000002", AT);

        assertThat(activity.listForUser(USER, null, 50)).extracting(Notification::idNotification)
                .containsExactly("act0000001");
        assertThat(activity.findForUser(USER, "act0000003")).isEmpty();
    }

    @Test
    @DisplayName("a notification is found only for its own recipient")
    void foundOnlyForItsRecipient() {
        saved("act0000001", USER, CASA, NotificationSourceType.HOME, 0);

        assertThat(activity.findForUser(USER, "act0000001")).isPresent();
        assertThat(activity.findForUser(OTHER_USER, "act0000001")).isEmpty();
    }

    @Test
    @DisplayName("marks read one, or every unread one of a home")
    void marksRead() {
        saved("act0000001", USER, CASA, NotificationSourceType.HOME, 0);
        saved("act0000002", USER, CASA, NotificationSourceType.DEVICE, 1);
        saved("act0000003", USER, FINCA, NotificationSourceType.HOME, 2);

        activity.markRead("act0000001", AT);
        int marked = activity.markAllRead(USER, CASA, AT.plusHours(1));

        assertThat(marked).as("only the unread one of Casa").isEqualTo(1);
        assertThat(notifications.findById("act0000001").orElseThrow().readAt()).isEqualTo(AT);
        assertThat(notifications.findById("act0000002").orElseThrow().readAt()).isNotNull();
        assertThat(notifications.findById("act0000003").orElseThrow().readAt()).isNull();
    }
}
