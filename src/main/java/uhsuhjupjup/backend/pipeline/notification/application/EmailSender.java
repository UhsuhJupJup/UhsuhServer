package uhsuhjupjup.backend.pipeline.notification.application;

import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

public interface EmailSender {

    void send(EmailMessage message);
}
