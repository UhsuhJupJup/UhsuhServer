package uhsuhjupjup.backend.pipeline.notification.infra;

import java.time.LocalDateTime;

public interface NotificationOutboxDailyRow {

    String getLogDate();

    LocalDateTime getFirstSentAt();

    long getTotal();

    long getMemberCount();

    long getSubscriberCount();
}
