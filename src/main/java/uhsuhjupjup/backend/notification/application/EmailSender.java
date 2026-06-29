package uhsuhjupjup.backend.notification.application;

import uhsuhjupjup.backend.notification.application.dto.EmailMessage;

public interface EmailSender {

    void send(EmailMessage message);
}
