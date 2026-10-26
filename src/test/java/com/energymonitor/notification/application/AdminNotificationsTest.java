package com.energymonitor.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.energymonitor.notification.application.port.in.AccountAdministered;
import com.energymonitor.notification.application.port.in.AccountRegistered;
import com.energymonitor.notification.application.port.out.AdminNotificationPort;
import com.energymonitor.notification.application.usecase.AdminNotifications;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.NotificationStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Administrator notifications: who receives each event, and that every read is scoped to the
 * caller.
 */
class AdminNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    private final List<String> admins = new ArrayList<>(List.of("adm0000001", "adm0000002"));
    private final InMemoryAdminNotifications store = new InMemoryAdminNotifications();
    private final AtomicInteger ids = new AtomicInteger();
    private final AdminNotifications service = new AdminNotifications(() -> List.copyOf(admins),
            store, () -> String.format("not%07d", ids.incrementAndGet()));

    @Test
    void aNewAccountIsAnnouncedToEveryAdministrator() {
        service.record(new AccountRegistered("use0000009", "new@example.com", NOW));

        assertThat(store.rows.values())
                .extracting(Notification::userId, Notification::sourceId, Notification::messageKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("adm0000001", "use0000009", "admin.accountRegistered"),
                        org.assertj.core.groups.Tuple.tuple("adm0000002", "use0000009", "admin.accountRegistered"));
        assertThat(store.rows.values()).allSatisfy(n -> {
            assertThat(n.channel()).isEqualTo(NotificationChannel.IN_APP);
            assertThat(n.sourceType()).isEqualTo(NotificationSourceType.ADMINISTRATION);
            assertThat(n.deliveryStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.readAt()).isNull();
        });
    }

    @Test
    void aChangeIsAnnouncedToTheOtherAdministratorsButNotToItsAuthor() {
        service.record(new AccountAdministered("adm0000001", "use0000009",
                AccountAdministered.Action.STATUS_CHANGED, "BLOCKED", NOW));

        assertThat(store.rows.values()).extracting(Notification::userId, Notification::messageKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("adm0000002",
                        "admin.statusChanged.BLOCKED"));
    }

    @Test
    void roleChangesCarryTheRoleInTheKey() {
        service.record(new AccountAdministered("adm0000001", "use0000009",
                AccountAdministered.Action.ROLE_GRANTED, "ADMIN", NOW));
        service.record(new AccountAdministered("adm0000001", "use0000009",
                AccountAdministered.Action.ROLE_REVOKED, "ADMIN", NOW));

        assertThat(store.rows.values()).extracting(Notification::messageKey)
                .containsExactlyInAnyOrder("admin.roleGranted.ADMIN", "admin.roleRevoked.ADMIN");
    }

    @Test
    void anEventDeliveredTwiceIsWrittenOnce() {
        var event = new AccountRegistered("use0000009", "new@example.com", NOW);

        service.record(event);
        service.record(event);

        assertThat(store.rows).hasSize(2);
        assertThat(store.rows.values()).extracting(Notification::sentAt)
                .containsOnly(LocalDateTime.ofInstant(NOW, java.time.ZoneId.systemDefault()));
    }

    @Test
    void withoutAdministratorsNothingIsWritten() {
        admins.clear();

        service.record(new AccountRegistered("use0000009", "new@example.com", NOW));

        assertThat(store.rows).isEmpty();
    }

    @Test
    void anAdministratorReadsMarksAndDeletesOnlyTheirOwnNotifications() {
        service.record(new AccountRegistered("use0000009", "new@example.com", NOW));
        String mine = idOf("adm0000001");
        String theirs = idOf("adm0000002");

        assertThat(service.list("adm0000001")).extracting(Notification::idNotification)
                .containsExactly(mine);
        assertThatThrownBy(() -> service.markRead("adm0000001", theirs))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.delete("adm0000001", theirs))
                .isInstanceOf(NoSuchElementException.class);

        service.markRead("adm0000001", mine);
        assertThat(store.rows.get(mine).readAt()).isNotNull();
        assertThat(store.rows.get(theirs).readAt()).isNull();

        service.delete("adm0000001", mine);
        assertThat(service.list("adm0000001")).isEmpty();
        assertThat(service.list("adm0000002")).hasSize(1);
    }

    private String idOf(String userId) {
        return store.rows.values().stream().filter(n -> n.userId().equals(userId))
                .findFirst().orElseThrow().idNotification();
    }

    /** Keeps rows by id; deleted rows are dropped, read rows get their instant. */
    private static final class InMemoryAdminNotifications implements AdminNotificationPort {

        private final Map<String, Notification> rows = new LinkedHashMap<>();

        @Override
        public void save(Notification notification) {
            rows.put(notification.idNotification(), notification);
        }

        @Override
        public boolean exists(String userId, String messageKey, String sourceId,
                              LocalDateTime sentAt) {
            return rows.values().stream().anyMatch(n -> n.userId().equals(userId)
                    && n.messageKey().equals(messageKey) && sourceId.equals(n.sourceId())
                    && sentAt.equals(n.sentAt()));
        }

        @Override
        public List<Notification> listForUser(String userId, int limit) {
            return rows.values().stream().filter(n -> n.userId().equals(userId))
                    .sorted(Comparator.comparing(Notification::sentAt).reversed())
                    .limit(limit).toList();
        }

        @Override
        public Optional<Notification> findForUser(String userId, String idNotification) {
            return Optional.ofNullable(rows.get(idNotification))
                    .filter(n -> n.userId().equals(userId));
        }

        @Override
        public void markRead(String idNotification, LocalDateTime readAt) {
            rows.get(idNotification).markAsRead(readAt);
        }

        @Override
        public void delete(String idNotification, LocalDateTime deletedAt) {
            rows.remove(idNotification);
        }
    }
}
