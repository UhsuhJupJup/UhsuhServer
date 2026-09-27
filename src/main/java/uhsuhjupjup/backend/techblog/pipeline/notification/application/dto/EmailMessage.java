package uhsuhjupjup.backend.techblog.pipeline.notification.application.dto;

public record EmailMessage(String to, String subject, String htmlBody, String unsubscribeUrl) {
}
