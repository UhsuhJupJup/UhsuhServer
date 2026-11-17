package uhsuhjupjup.backend.pipeline.notification.infra;

import java.time.LocalDateTime;

public interface NotificationSentLogRow {

    Long getId();

    String getRecipient();

    String getRecipientType();

    int getArticleCount();

    String getSubject();

    LocalDateTime getSentAt();
}
