package uhsuhjupjup.backend.notification.application;

import uhsuhjupjup.backend.notification.application.dto.EmailMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FakeEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final Set<String> failingRecipients = new HashSet<>();

    public void failFor(String email) {
        failingRecipients.add(email);
    }

    @Override
    public void send(EmailMessage message) {
        if (failingRecipients.contains(message.to())) {
            throw new RuntimeException("boom: " + message.to());
        }
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return sent;
    }
}
