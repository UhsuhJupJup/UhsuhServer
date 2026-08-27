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

    @Query("select o from NotificationOutbox o"
            + " where o.status = :status and o.nextAttemptAt <= :now"
            + " order by o.nextAttemptAt asc")
    List<NotificationOutbox> findDue(@Param("status") OutboxStatus status,
                                     @Param("now") LocalDateTime now,
                                     Pageable pageable);
}
