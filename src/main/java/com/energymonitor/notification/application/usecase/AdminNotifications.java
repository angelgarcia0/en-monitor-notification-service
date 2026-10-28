package com.energymonitor.notification.application.usecase;

import com.energymonitor.notification.application.port.in.AccountAdministered;
import com.energymonitor.notification.application.port.in.AccountRegistered;
import com.energymonitor.notification.application.port.out.AdminNotificationPort;
import com.energymonitor.notification.application.port.out.AdministratorQuery;
import com.energymonitor.notification.application.port.out.NotificationIdentifierPort;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * The in-app notifications addressed to administrators.
 *
 * <p>Each platform event becomes one row per active administrator, except the administrator who
 * caused it. Only the key of the message and the affected account are stored, as in the rest of
 * the log; the web builds the text. Nothing is sent by mail or push.
 *
 * <p>Reading, marking and deleting are always scoped to the caller, so an administrator never
 * reaches another administrator's notifications.
 */
@Component
public class AdminNotifications {

    /** Most notifications returned by {@link #list}. */
    public static final int LIST_LIMIT = 50;

    private final AdministratorQuery administrators;
    private final AdminNotificationPort notifications;
    private final NotificationIdentifierPort identifiers;

    public AdminNotifications(AdministratorQuery administrators,
                              AdminNotificationPort notifications,
                              NotificationIdentifierPort identifiers) {
        this.administrators = administrators;
        this.notifications = notifications;
        this.identifiers = identifiers;
    }

    /** A new account: every administrator is told. */
    public void record(AccountRegistered event) {
        deliver(null, event.userId(), "admin.accountRegistered", event.occurredAt());
    }

    /** A change an administrator made: every other administrator is told. */
    public void record(AccountAdministered event) {
        String key = switch (event.action()) {
            case STATUS_CHANGED -> "admin.statusChanged." + event.detail();
            case ROLE_GRANTED -> "admin.roleGranted." + event.detail();
            case ROLE_REVOKED -> "admin.roleRevoked." + event.detail();
        };
        deliver(event.actorId(), event.userId(), key, event.occurredAt());
    }

    /**
     * @param userId the administrator
     * @return their notifications, newest first, at most {@link #LIST_LIMIT}
     */
    public List<Notification> list(String userId) {
        return notifications.listForUser(userId, LIST_LIMIT);
    }

    /**
     * @throws NoSuchElementException when the notification is not the caller's
     */
    public void markRead(String userId, String idNotification) {
        requireOwn(userId, idNotification);
        notifications.markRead(idNotification, LocalDateTime.now());
    }

    /**
     * @throws NoSuchElementException when the notification is not the caller's
     */
    public void delete(String userId, String idNotification) {
        requireOwn(userId, idNotification);
        notifications.delete(idNotification, LocalDateTime.now());
    }

    /**
     * Writes one notification per administrator, stamped with the instant of the event rather
     * than the instant it arrived. The event may be delivered twice by the message broker, and
     * that stamp is what lets the second delivery find the first one and stop. It is cut to the
     * second because the column stores no fraction.
     */
    private void deliver(String actorId, String subjectId, String messageKey, Instant occurredAt) {
        LocalDateTime sentAt = LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS);
        administrators.activeAdministratorIds().stream()
                .filter(adminId -> !adminId.equals(actorId))
                .filter(adminId -> !notifications.exists(adminId, messageKey, subjectId, sentAt))
                .forEach(adminId -> {
                    Notification notification = Notification.pending(identifiers.generate(),
                            adminId, null, NotificationChannel.IN_APP,
                            NotificationSourceType.ADMINISTRATION, subjectId, messageKey);
                    notification.markAsSent(sentAt);
                    notifications.save(notification);
                });
    }

    private void requireOwn(String userId, String idNotification) {
        if (notifications.findForUser(userId, idNotification).isEmpty()) {
            throw new NoSuchElementException("no notification " + idNotification);
        }
    }
}
