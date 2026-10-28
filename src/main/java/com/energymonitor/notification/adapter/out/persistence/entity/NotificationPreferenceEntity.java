package com.energymonitor.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_preference")
public class NotificationPreferenceEntity extends BaseAuditEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 10)
    private String userId;

    // MySQL BOOLEAN is TINYINT(1); without this Hibernate expects BIT and schema validation fails.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    protected NotificationPreferenceEntity() {
    }

    public NotificationPreferenceEntity(String userId, boolean emailEnabled, boolean pushEnabled) {
        this.userId = userId;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }
}
