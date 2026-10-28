package com.energymonitor.notification.adapter.out.persistence.repository;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, String> {
}
