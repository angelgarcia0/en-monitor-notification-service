package com.energymonitor.notification.adapter.out.persistence.repository;

import com.energymonitor.notification.adapter.out.persistence.entity.NotificationEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code notification}.
 *
 * <p>No finder takes a message body, so no code path can put one in a query. The bodies live only in
 * the message that left, never in the row that records that it did.
 */
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    /**
     * The pending rows opened before a cutoff, oldest first.
     *
     * <p>Ordered so a sweep that has to stop part way closes the oldest first, which are the ones
     * that have been lying longest.
     *
     * @param openedBefore the cutoff, exclusive
     * @param limit        how many rows to return
     * @return the abandoned rows
     */
    List<NotificationEntity> findByDeliveryStatusAndCreatedAtLessThanOrderByCreatedAtAsc(
            com.energymonitor.notification.domain.model.NotificationStatus status,
            LocalDateTime openedBefore,
            org.springframework.data.domain.Pageable limit);

    /**
     * Closes every pending row older than a cutoff, and only those.
     *
     * <p>The {@code status} predicate is what makes the sweep safe to run while deliveries are still
     * going out: a row that completed a moment ago is no longer pending and is left alone.
     *
     * @param status       the state to close, always pending
     * @param openedBefore the cutoff, exclusive
     * @param reason       recorded on each row it closes
     * @return how many rows were closed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.deliveryStatus = :failed, n.failureReason = :reason"
            + " where n.deliveryStatus = :pending and n.createdAt < :openedBefore" 
            + " and n.deletedAt is null")
    int failAbandoned(@Param("pending") com.energymonitor.notification.domain.model.NotificationStatus pending,
            @Param("openedBefore") LocalDateTime openedBefore,
            @Param("reason") String reason,
            @Param("failed") com.energymonitor.notification.domain.model.NotificationStatus failed);

    /**
     * A recipient's notifications of one channel and source that are not deleted, newest first.
     *
     * @param userId the recipient
     * @param channel the channel
     * @param sourceType the source
     * @param page how many to return
     * @return the rows
     */
    List<NotificationEntity> findByUserIdAndChannelAndSourceTypeAndDeletedAtIsNullOrderBySentAtDesc(
            String userId, com.energymonitor.notification.domain.model.NotificationChannel channel,
            com.energymonitor.notification.domain.model.NotificationSourceType sourceType,
            org.springframework.data.domain.Pageable page);

    /**
     * Whether a recipient already has a notification of one channel and source, about one row,
     * with one message, sent at one instant. Deleted rows count, so a notification the recipient
     * already dismissed is not written again when its event is delivered twice.
     */
    boolean existsByUserIdAndChannelAndSourceTypeAndSourceIdAndMessageKeyAndSentAt(
            String userId, com.energymonitor.notification.domain.model.NotificationChannel channel,
            com.energymonitor.notification.domain.model.NotificationSourceType sourceType,
            String sourceId, String messageKey, java.time.LocalDateTime sentAt);

    /**
     * Whether any row, of any recipient and channel, refers to one source row. Deleted rows count.
     */
    boolean existsBySourceTypeAndSourceId(
            com.energymonitor.notification.domain.model.NotificationSourceType sourceType,
            String sourceId);

    /**
     * A recipient's in-app notifications of some sources that are not deleted, newest first.
     *
     * @param homeId one home, or null for all of them and those of no home
     */
    @Query("select n from NotificationEntity n where n.userId = :userId and n.channel = :channel"
            + " and n.sourceType in :sources and n.deletedAt is null"
            + " and (:homeId is null or n.homeId = :homeId)"
            + " order by n.sentAt desc, n.idNotification desc")
    List<NotificationEntity> findInApp(@Param("userId") String userId,
            @Param("channel") com.energymonitor.notification.domain.model.NotificationChannel channel,
            @Param("sources") Collection<com.energymonitor.notification.domain.model.NotificationSourceType> sources,
            @Param("homeId") String homeId,
            org.springframework.data.domain.Pageable page);

    /**
     * Stamps the read instant on a recipient's unread in-app notifications of some sources.
     *
     * @param homeId one home, or null for all of them and those of no home
     * @return how many were marked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationEntity n set n.readAt = :readAt where n.userId = :userId"
            + " and n.channel = :channel and n.sourceType in :sources and n.deletedAt is null"
            + " and n.readAt is null and (:homeId is null or n.homeId = :homeId)")
    int markInAppRead(@Param("userId") String userId,
            @Param("channel") com.energymonitor.notification.domain.model.NotificationChannel channel,
            @Param("sources") Collection<com.energymonitor.notification.domain.model.NotificationSourceType> sources,
            @Param("homeId") String homeId,
            @Param("readAt") LocalDateTime readAt);
}
