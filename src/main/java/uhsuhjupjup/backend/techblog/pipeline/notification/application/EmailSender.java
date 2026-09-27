package uhsuhjupjup.backend.techblog.pipeline.notification.application;

import uhsuhjupjup.backend.techblog.pipeline.notification.application.dto.EmailMessage;

public interface EmailSender {

    void send(EmailMessage message);
}
