package uhsuhjupjup.backend.pipeline.notification.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    long countByStatus(OutboxStatus status);

    List<NotificationOutbox> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    List<NotificationOutbox> findByStatusOrderBySentAtDesc(OutboxStatus status, Pageable pageable);

    List<NotificationOutbox> findByStatusAndSentAtGreaterThanEqualAndSentAtLessThanOrderBySentAtDesc(
            OutboxStatus status, LocalDateTime from, LocalDateTime to);

    @Query(value = "select date_format(sent_at, '%Y-%m-%d') as logDate,"
            + " min(sent_at) as firstSentAt,"
            + " count(*) as total,"
            + " cast(sum(recipient_type = 'MEMBER') as signed) as memberCount,"
            + " cast(sum(recipient_type = 'EMAIL_SUBSCRIBER') as signed) as subscriberCount"
            + " from notification_outbox"
            + " where status = 'SENT'"
            + " group by date_format(sent_at, '%Y-%m-%d')"
            + " order by logDate desc", nativeQuery = true)
    List<NotificationOutboxDailyRow> findDailySentSummary();

    @Query(value = "select id from notification_outbox"
            + " where status = :status and next_attempt_at <= :now"
            + " order by next_attempt_at asc"
            + " limit :limit"
            + " for update skip locked", nativeQuery = true)
    List<Long> lockDueIds(@Param("status") String status,
                          @Param("now") LocalDateTime now,
                          @Param("limit") int limit);

    @Query(value = "select id from notification_outbox"
            + " where status = 'PENDING' and republish_at <= :now"
            + " order by republish_at asc"
            + " limit :limit"
            + " for update skip locked", nativeQuery = true)
    List<Long> lockRepublishablePendingIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = "select id from notification_outbox"
            + " where status = 'PROCESSING' and next_attempt_at <= :now and republish_at <= :now"
            + " order by next_attempt_at asc"
            + " limit :limit"
            + " for update skip locked", nativeQuery = true)
    List<Long> lockExpiredProcessingIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o set o.republishAt = :at where o.id in :ids")
    int deferRepublishAll(@Param("ids") List<Long> ids, @Param("at") LocalDateTime at);

    @Query("select o.id from NotificationOutbox o where o.id in :ids and o.attempts >= :maxAttempts")
    List<Long> findIdsReachedAttempts(@Param("ids") List<Long> ids, @Param("maxAttempts") int maxAttempts);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o"
            + " set o.status = :status, o.attempts = o.attempts + 1, o.nextAttemptAt = :leaseUntil"
            + " where o.id in :ids")
    int claimAll(@Param("ids") List<Long> ids,
                 @Param("status") OutboxStatus status,
                 @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o"
            + " set o.status = :processing, o.attempts = o.attempts + 1, o.nextAttemptAt = :leaseUntil"
            + " where o.id = :id and ("
            + " (o.status = :pending and (o.attempts = 0 or o.nextAttemptAt <= :now))"
            + " or (o.status = :processing and o.nextAttemptAt <= :now and o.attempts < :maxAttempts))")
    int claimOne(@Param("id") Long id,
                 @Param("pending") OutboxStatus pending,
                 @Param("processing") OutboxStatus processing,
                 @Param("now") LocalDateTime now,
                 @Param("leaseUntil") LocalDateTime leaseUntil,
                 @Param("maxAttempts") int maxAttempts);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o set o.status = :status, o.lastError = :error where o.id in :ids")
    int failAll(@Param("ids") List<Long> ids,
                @Param("status") OutboxStatus status,
                @Param("error") String error);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationOutbox o"
            + " set o.status = :to, o.attempts = o.attempts - 1, o.nextAttemptAt = :now, o.republishAt = :now"
            + " where o.id in :ids and o.status = :from")
    int releaseAll(@Param("ids") List<Long> ids,
                   @Param("from") OutboxStatus from,
                   @Param("to") OutboxStatus to,
                   @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notification_outbox"
            + " where status = 'SENT' and sent_at < :before"
            + " order by sent_at asc"
            + " limit :limit", nativeQuery = true)
    int deleteSentBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
