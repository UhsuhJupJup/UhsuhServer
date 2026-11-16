package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final EmailSender emailSender;
    private final OutboxResultRecorder outboxResultRecorder;

    public boolean dispatchClaimed(Long id) {
        NotificationOutbox row = notificationOutboxRepository.findById(id).orElse(null);
        if (row == null || !row.isProcessing()) {
            return false;
        }
        try {
            emailSender.send(new EmailMessage(row.getRecipient(), row.getSubject(), row.getBody(),
                    row.getUnsubscribeUrl()));
        } catch (EmailSendException e) {
            outboxResultRecorder.recordFailure(id, e.getMessage(), e.isPermanent(), LocalDateTime.now());
            return false;
        } catch (Exception e) {
            outboxResultRecorder.recordFailure(id, e.getMessage(), false, LocalDateTime.now());
            return false;
        }
        outboxResultRecorder.recordSent(id, LocalDateTime.now());
        return true;
    }
}
