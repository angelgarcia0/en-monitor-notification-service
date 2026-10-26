package com.energymonitor.notification.adapter.out.persistence.entity;

import com.energymonitor.notification.domain.model.PushDevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_device")
public class PushDeviceEntity extends BaseAuditEntity {

    @Id
    @Column(name = "id_push_device", nullable = false, length = 10)
    private String idDevice;

    @Column(name = "user_id", nullable = false, length = 10)
    private String userId;

    @Column(name = "token", nullable = false, length = 255, unique = true)
    private String token;

    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    protected PushDeviceEntity() {
    }

    public PushDeviceEntity(String idDevice, String userId, String token, PushDevicePlatform platform) {
        this.idDevice = idDevice;
        this.userId = userId;
        this.token = token;
        this.platform = platform.name();
    }

    public String getIdDevice() {
        return idDevice;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public PushDevicePlatform platformEnum() {
        return PushDevicePlatform.valueOf(platform);
    }
}