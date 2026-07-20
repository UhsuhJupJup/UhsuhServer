package uhsuhjupjup.backend.pipeline.run.ui.dto;

import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;

import java.time.Duration;
import java.time.LocalDateTime;

public record PipelineRunResponse(
        Long id,
        String kind,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String status,
        long durationSeconds,
        int collectedTotal,
        int collectedNew,
        int collectFailed,
        int matchedArticles,
        int tagsCreated,
        int membersNotified,
        int notificationsRecorded,
        int notifyFailed) {

    public static PipelineRunResponse from(PipelineRun run) {
        return new PipelineRunResponse(
                run.getId(),
                run.getKind().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus().name(),
                Duration.between(run.getStartedAt(), run.getFinishedAt()).getSeconds(),
                run.getCollectedTotal(),
                run.getCollectedNew(),
                run.getCollectFailed(),
                run.getMatchedArticles(),
                run.getTagsCreated(),
                run.getMembersNotified(),
                run.getNotificationsRecorded(),
                run.getNotifyFailed());
    }
}
