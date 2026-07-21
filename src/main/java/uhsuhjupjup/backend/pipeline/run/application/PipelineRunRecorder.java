package uhsuhjupjup.backend.pipeline.run.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;
import uhsuhjupjup.backend.pipeline.run.domain.RunKind;
import uhsuhjupjup.backend.pipeline.run.domain.RunStatus;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PipelineRunRecorder {

    private final PipelineRunRepository pipelineRunRepository;

    @Transactional
    public void recordIngest(LocalDateTime startedAt, LocalDateTime finishedAt,
                             CollectionResult collection, MatchingResult matching) {
        PipelineRun run = PipelineRun.builder()
                .kind(RunKind.INGEST)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .status(ingestStatus(collection, matching))
                .collectedTotal(collection == null ? 0 : collection.total())
                .collectedNew(collection == null ? 0 : collection.newArticles())
                .collectFailed(collection == null ? 0 : collection.transientFailed() + collection.permanentFailed())
                .matchedArticles(matching == null ? 0 : matching.articlesTagged())
                .tagsCreated(matching == null ? 0 : matching.tagsCreated())
                .build();
        pipelineRunRepository.save(run);
    }

    @Transactional
    public void recordNotification(LocalDateTime startedAt, LocalDateTime finishedAt,
                                   NotificationResult notification) {
        PipelineRun run = PipelineRun.builder()
                .kind(RunKind.NOTIFICATION)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .status(notification == null ? RunStatus.FAILED : RunStatus.SUCCESS)
                .membersNotified(notification == null ? 0 : notification.membersNotified())
                .notificationsRecorded(notification == null ? 0 : notification.notificationsRecorded())
                .notifyFailed(notification == null ? 0 : notification.failedMembers())
                .build();
        pipelineRunRepository.save(run);
    }

    private RunStatus ingestStatus(CollectionResult collection, MatchingResult matching) {
        int failed = (collection == null ? 1 : 0) + (matching == null ? 1 : 0);
        if (failed == 0) {
            return RunStatus.SUCCESS;
        }
        if (failed == 2) {
            return RunStatus.FAILED;
        }
        return RunStatus.PARTIAL;
    }
}
