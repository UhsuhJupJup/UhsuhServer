package uhsuhjupjup.backend.pipeline.notification.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select o from NotificationOutbox o"
            + " where o.status = :status and o.nextAttemptAt <= :now"
            + " order by o.nextAttemptAt asc")
    List<NotificationOutbox> findDue(@Param("status") OutboxStatus status,
                                     @Param("now") LocalDateTime now,
                                     Pageable pageable);
}
