package com.energymonitor.notification.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A record that an outbound message was attempted, and how it ended.
 *
 * <p>This is a delivery log, not the event that caused the message. An alert and a
 * recommendation each live in their own module and keep their own lifecycle; this model answers
 * only the question none of them can: was a message actually sent, over which channel, and did
 * it fail.
 *
 * <p><strong>The message body is deliberately not stored.</strong> A password recovery secret
 * travels in that body, and a column holding it in clear text would undo the decision to keep
 * only a hash elsewhere. What is stored instead is {@link #messageKey()}: a stable name for the
 * wording that was used, which is enough to explain the row to somebody reading it during an
 * audit and is not itself the message.
 *
 * <p>The source is named by {@link #sourceType()} and {@link #sourceId()} rather than by a
 * foreign key. Both point into another module's table, and a foreign key would make this module
 * depend on that schema at the database level, which is the coupling the module boundary exists to
 * avoid. The identifiers are therefore references without integrity: nothing here can check that
 * the row they name still exists, and nothing here should.
 *
 * <p>{@link #userId()} is the one reference that is not optional, because a message with no
 * recipient on file cannot be traced back to anybody. {@link #homeId()} is optional for the
 * messages that belong to an account and not to a home, such as a recovery code.
 *
 * <p>Invariants:
 * <ul>
 *   <li>NOTIF-INV-001: a notification is created {@code PENDING}, never directly {@code SENT}.</li>
 *   <li>NOTIF-INV-002: {@code SENT} carries the instant it was sent.</li>
 *   <li>NOTIF-INV-003: {@code FAILED} carries a reason.</li>
 *   <li>NOTIF-INV-004: {@code SENT} carries no reason, because it did not fail.</li>
 *   <li>NOTIF-INV-005: a notification always names a user.</li>
 * </ul>
 *
 * <p>{@link #readAt()} is part of the documented model and the column exists, but nothing writes it
 * yet: no endpoint reports that a message was opened, so the field is always null. It is kept
 * because the model asks for it and adding the column later is a migration; the one thing to know
 * is that a non-null value cannot be expected today.
 */
public class Notification {

    private final String idNotification;
    private final String userId;
    private final String homeId;
    private final NotificationChannel channel;
    private final NotificationSourceType sourceType;
    private final String sourceId;
    private final String messageKey;
    private NotificationStatus deliveryStatus;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private String failureReason;
    private LocalDateTime deletedAt;

    /**
     * Rehydrates a stored notification.
     *
     * @param idNotification identifier, {@code VARCHAR(10)}
     * @param userId         the account the message is for, {@code VARCHAR(10)}
     * @param homeId         the home it concerns, optional
     * @param channel        delivery channel
     * @param sourceType     the context the message originates from
     * @param sourceId       the row in that context, optional
     * @param messageKey     stable name of the wording used
     * @param status         current delivery status
     * @param sentAt         when it was sent, present only when sent
     * @param readAt         when it was read, absent while unread
     * @param failureReason  why it did not go out, present only when failed
     * @param deletedAt      soft delete marker
     */
    public Notification(String idNotification, String userId, String homeId,
                        NotificationChannel channel, NotificationSourceType sourceType,
                        String sourceId, String messageKey, NotificationStatus deliveryStatus,
                        LocalDateTime sentAt, LocalDateTime readAt, String failureReason,
                        LocalDateTime deletedAt) {
        this.idNotification = Preconditions.text(idNotification, 10, "idNotification");
        this.userId = Preconditions.text(userId, 10, "userId");
        this.homeId = optional(homeId, 10, "homeId");
        this.channel = Preconditions.notNull(channel, "channel");
        this.sourceType = Preconditions.notNull(sourceType, "sourceType");
        this.sourceId = optional(sourceId, 10, "sourceId");
        this.messageKey = Preconditions.text(messageKey, 100, "messageKey");
        this.deliveryStatus = Preconditions.notNull(deliveryStatus, "deliveryStatus");
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.failureReason = failureReason;
        this.deletedAt = deletedAt;
    }

    /**
     * Normalises an optional identifier: absent becomes {@code null} rather than an empty string,
     * so "no home" and "a home whose name is blank" cannot be told apart by accident.
     */
    private static String optional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Preconditions.text(value.trim(), maxLength, field);
    }

    /**
     * Creates a notification that has not been attempted yet (NOTIF-INV-001).
     *
     * @param idNotification identifier
     * @param userId         the account the message is for
     * @param homeId         the home it concerns, optional
     * @param channel        delivery channel
     * @param sourceType     the context the message originates from
     * @param sourceId       the row in that context, optional
     * @param messageKey     stable name of the wording used
     * @return a pending notification
     */
    public static Notification pending(String idNotification, String userId, String homeId,
                                       NotificationChannel channel,
                                       NotificationSourceType sourceType, String sourceId,
                                       String messageKey) {
        return new Notification(idNotification, userId, homeId, channel, sourceType, sourceId,
                messageKey, NotificationStatus.PENDING, null, null, null, null);
    }

    /**
     * Records a successful delivery.
     *
     * @param sentAt when the message left, clears any previous reason (NOTIF-INV-002, -004)
     */
    public void markAsSent(LocalDateTime sentAt) {
        Preconditions.notNull(sentAt, "sentAt");
        this.deliveryStatus = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.failureReason = null;
    }

    /**
     * Records a failed delivery. The reason is truncated to the column width rather than
     * rejected, because a driver or server message is not worth failing a log write over.
     *
     * @param reason why the message did not go out (NOTIF-INV-003)
     */
    public void markAsFailed(String reason) {
        this.deliveryStatus = NotificationStatus.FAILED;
        this.failureReason = failureReason(reason);
    }

    /**
     * Records that the recipient opened the message.
     *
     * <p>Only meaningful once it has been sent, and only the first time counts: a message opened
     * twice is still a message that was read once, and storing the second instant would lose the
     * one that answers when the user first saw it.
     *
     * @param readAt when it was opened
     * @throws IllegalStateException if the message was never sent, since an unread delivery has
     *                               nothing to be the reading of
     */
    public void markAsRead(LocalDateTime readAt) {
        Preconditions.notNull(readAt, "readAt");
        if (deliveryStatus != NotificationStatus.SENT) {
            throw new IllegalStateException(
                    "only a delivered notification can be marked as read, this one is "
                            + deliveryStatus);
        }
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }

    /**
     * Normalises a failure reason to the width of the {@code failure_reason} column.
     *
     * <p>Exposed because a failure is recorded by updating an already stored row, where the
     * domain object is never instantiated, and the truncation rule must not be applied twice in
     * two places with two different limits.
     *
     * @param reason the raw reason, which may come from a driver or mail server
     * @return the reason, trimmed and cut to {@value #MAX_REASON} characters
     */
    public static String failureReason(String reason) {
        String text = Preconditions.text(reason, Integer.MAX_VALUE, "reason");
        return text.length() > MAX_REASON ? text.substring(0, MAX_REASON) : text;
    }

    /** Matches the {@code failure_reason} column width. */
    private static final int MAX_REASON = 200;

    public String idNotification() {
        return idNotification;
    }

    /** @return the account the message is for */
    public String userId() {
        return userId;
    }

    /** @return the home it concerns, or null when it concerns an account and not a home */
    public String homeId() {
        return homeId;
    }

    public NotificationChannel channel() {
        return channel;
    }

    /** @return the context this message originates from */
    public NotificationSourceType sourceType() {
        return sourceType;
    }

    /** @return the row in that context, or null when the message is not about one */
    public String sourceId() {
        return sourceId;
    }

    /** @return the stable name of the wording that was used */
    public String messageKey() {
        return messageKey;
    }

    public NotificationStatus deliveryStatus() {
        return deliveryStatus;
    }

    /** @return why the delivery failed, present only when failed */
    public String failureReason() {
        return failureReason;
    }

    public LocalDateTime sentAt() {
        return sentAt;
    }

    /** @return when the message was opened, or null while it has not been */
    public LocalDateTime readAt() {
        return readAt;
    }

    public LocalDateTime deletedAt() {
        return deletedAt;
    }

    /**
     * Identity is the identifier alone: two rows describing the same user and the same source row
     * are different notifications, and the primary key is the only thing that says so.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Notification that && Objects.equals(idNotification, that.idNotification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idNotification);
    }
}
