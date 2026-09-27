package uhsuhjupjup.backend.techblog.pipeline.notification.application.dto;

public record NotificationResult(int membersNotified, int emailSubscribersNotified,
                                 int notificationsRecorded, int failedMembers) {
}
