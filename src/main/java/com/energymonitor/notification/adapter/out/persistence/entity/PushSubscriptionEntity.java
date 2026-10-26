package com.energymonitor.notification.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_subscription")
public class PushSubscriptionEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id_push_subscription", nullable = false, length = 10)
    private String idSubscription;

    @Column(name = "user_id", nullable = false, length = 10)
    private String userId;

    @Column(name = "endpoint", nullable = false, length = 500, unique = true)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 100)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 50)
    private String auth;

    protected PushSubscriptionEntity() {
    }

    public PushSubscriptionEntity(String idSubscription, String userId, String endpoint, String p256dh, String auth) {
        this.idSubscription = idSubscription;
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public String getIdSubscription() {
        return idSubscription;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }
}
