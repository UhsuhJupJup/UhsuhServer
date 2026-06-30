package uhsuhjupjup.backend.pipeline.run.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;
import uhsuhjupjup.backend.pipeline.run.domain.RunStatus;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PipelineRunRecorder {

    private final PipelineRunRepository pipelineRunRepository;

    @Transactional
    public void record(LocalDateTime startedAt, LocalDateTime finishedAt,
                       CollectionResult collection, MatchingResult matching, NotificationResult notification) {
        PipelineRun run = PipelineRun.builder()
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .status(statusOf(collection, matching, notification))
                .collectedTotal(collection == null ? 0 : collection.total())
                .collectedNew(collection == null ? 0 : collection.newArticles())
                .collectFailed(collection == null ? 0 : collection.transientFailed() + collection.permanentFailed())
                .matchedArticles(matching == null ? 0 : matching.articlesTagged())
                .tagsCreated(matching == null ? 0 : matching.tagsCreated())
                .membersNotified(notification == null ? 0 : notification.membersNotified())
                .notificationsRecorded(notification == null ? 0 : notification.notificationsRecorded())
                .notifyFailed(notification == null ? 0 : notification.failedMembers())
                .build();
        pipelineRunRepository.save(run);
    }

    private RunStatus statusOf(CollectionResult collection, MatchingResult matching, NotificationResult notification) {
        int failed = (collection == null ? 1 : 0) + (matching == null ? 1 : 0) + (notification == null ? 1 : 0);
        if (failed == 0) {
            return RunStatus.SUCCESS;
        }
        if (failed == 3) {
            return RunStatus.FAILED;
        }
        return RunStatus.PARTIAL;
    }
}
