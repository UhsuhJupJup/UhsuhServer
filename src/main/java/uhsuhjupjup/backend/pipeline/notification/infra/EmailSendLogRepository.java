package uhsuhjupjup.backend.pipeline.notification.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.pipeline.notification.domain.EmailSendLog;

import java.util.List;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long> {

    List<EmailSendLog> findAllByOrderBySentAtDesc(Pageable pageable);
}
