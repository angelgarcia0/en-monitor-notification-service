package com.energymonitor.notification.adapter.out.persistence.repository;

import com.energymonitor.notification.adapter.out.persistence.entity.PushSubscriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, String> {

    List<PushSubscriptionEntity> findByUserId(String userId);

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PushSubscriptionEntity s where s.endpoint = :endpoint")
    void deleteByEndpoint(@Param("endpoint") String endpoint);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PushSubscriptionEntity s where s.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
