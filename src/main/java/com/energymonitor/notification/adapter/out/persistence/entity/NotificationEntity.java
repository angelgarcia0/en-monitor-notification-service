package com.energymonitor.notification.adapter.out.persistence.entity;

import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.domain.model.NotificationStatus;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * JPA mapping of the {@code notification} table.
 *
 * <p>The enums are stored as strings rather than as ordinals: the schema declares them as MySQL
 * {@code ENUM} and the JPA side agrees by name, so reordering a Java enum can never silently
 * reinterpret existing rows.
 */
@Entity
@Table(name = "notification")
public class NotificationEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id_notification", nullable = false, length = 10)
    private String idNotification;

    /**
     * The account the message is for, referenced by identifier and not by a foreign key.
     *
     * <p>No constraint on purpose: the row belongs to the security service, and a foreign key
     * would make this module unable to be created, migrated or reasoned about without that schema
     * beside it. The reference is by identifier only.
     */
    @Column(name = "user_id", nullable = false, length = 10)
    private String userId;

    /**
     * The home the message concerns, absent for messages about an account rather than a home.
     *
     * <p>Also unconstrained, for the same reason and towards the same table in the home module.
     */
    @Column(name = "home_id", length = 10)
    private String homeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * The context this message originates from, naming which module owns the row it refers to.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private NotificationSourceType sourceType;

    /**
     * That row, by identifier. Absent when the message is not about a particular row.
     */
    @Column(name = "source_id", length = 10)
    private String sourceId;

    /**
     * Stable name of the wording that was used, never the wording itself.
     *
     * <p>The bodies are not stored anywhere in this table, which is what keeps a recovery code from
     * being readable by anybody who can read the table. The key is enough to say which version was
     * sent without saying what it said.
     */
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private NotificationStatus deliveryStatus;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    /** When the recipient opened the message, absent while it is unread. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** The values an in-app activity notification is worded with, as a JSON object; null otherwise. */
    @Column(name = "message_params", columnDefinition = "TEXT")
    private String messageParams;

    protected NotificationEntity() {
        // for JPA
    }

    public NotificationEntity(String idNotification, String userId, String homeId,
                              NotificationChannel channel, NotificationSourceType sourceType,
                              String sourceId, String messageKey,
                              NotificationStatus deliveryStatus, LocalDateTime sentAt,
                              LocalDateTime readAt, String failureReason) {
        this(idNotification, userId, homeId, channel, sourceType, sourceId, messageKey, null,
                deliveryStatus, sentAt, readAt, failureReason);
    }

    public NotificationEntity(String idNotification, String userId, String homeId,
                              NotificationChannel channel, NotificationSourceType sourceType,
                              String sourceId, String messageKey, String messageParams,
                              NotificationStatus deliveryStatus, LocalDateTime sentAt,
                              LocalDateTime readAt, String failureReason) {
        this.idNotification = idNotification;
        this.userId = userId;
        this.homeId = homeId;
        this.channel = channel;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.deliveryStatus = deliveryStatus;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.failureReason = failureReason;
    }

    public String getIdNotification() {
        return idNotification;
    }

    public String userId() {
        return userId;
    }

    public String homeId() {
        return homeId;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public NotificationSourceType sourceType() {
        return sourceType;
    }

    public String sourceId() {
        return sourceId;
    }

    public String messageKey() {
        return messageKey;
    }

    public String messageParams() {
        return messageParams;
    }

    public NotificationStatus deliveryStatus() {
        return deliveryStatus;
    }

    public String failureReason() {
        return failureReason;
    }

    public LocalDateTime sentAt() {
        return sentAt;
    }

    public LocalDateTime readAt() {
        return readAt;
    }

    /**
     * Records how the delivery ended.
     *
     * @param failureReason why it did not go out, or null when it did
     * @param sentAt        when it left, or null when it did not
     * @param status        the state it ended in
     */
    public void settled(String failureReason, LocalDateTime sentAt, NotificationStatus status) {
        this.failureReason = failureReason;
        this.sentAt = sentAt;
        this.deliveryStatus = status;
    }

    /**
     * Records that the message was opened, keeping the first instant.
     *
     * @param readAt when it was opened
     */
    public void read(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
