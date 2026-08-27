package uhsuhjupjup.backend.pipeline.notification.application;

import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FakeEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final Set<String> failingRecipients = new HashSet<>();
    private final Set<String> permanentFailingRecipients = new HashSet<>();

    public void failFor(String email) {
        failingRecipients.add(email);
    }

    public void failPermanentlyFor(String email) {
        permanentFailingRecipients.add(email);
    }

    @Override
    public void send(EmailMessage message) {
        if (permanentFailingRecipients.contains(message.to())) {
            throw new EmailSendException(message.to(), new RuntimeException("invalid address"), true);
        }
        if (failingRecipients.contains(message.to())) {
            throw new RuntimeException("boom: " + message.to());
        }
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return sent;
    }
}
