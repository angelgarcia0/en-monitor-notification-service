package com.energymonitor.notification.adapter.out.persistence.repository;

import com.energymonitor.notification.adapter.out.persistence.entity.PushDeviceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, String> {
    List<PushDeviceEntity> findByUserId(String userId);
    Optional<PushDeviceEntity> findByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PushDeviceEntity d where d.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PushDeviceEntity d where d.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}