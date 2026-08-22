package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.notification.domain.EmailSendLog;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.EmailSendLogRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailSendLogService {

    private final EmailSendLogRepository emailSendLogRepository;

    /** 발송 1건을 영구 기록. 발송마다 독립 커밋되도록 별도 트랜잭션. */
    @Transactional
    public void record(String email, RecipientType recipientType, int articleCount, String subject) {
        emailSendLogRepository.save(EmailSendLog.of(email, recipientType, articleCount, subject));
    }

    @Transactional(readOnly = true)
    public List<EmailSendLog> recentLogs(int limit) {
        return emailSendLogRepository.findAllByOrderBySentAtDesc(PageRequest.of(0, limit));
    }
}
