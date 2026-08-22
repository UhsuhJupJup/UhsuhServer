package uhsuhjupjup.backend.pipeline.run.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.pipeline.collection.application.dto.CollectionResult;
import uhsuhjupjup.backend.pipeline.matching.application.dto.MatchingResult;
import uhsuhjupjup.backend.pipeline.notification.application.dto.NotificationResult;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;
import uhsuhjupjup.backend.pipeline.run.domain.RunKind;
import uhsuhjupjup.backend.pipeline.run.domain.RunStatus;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineRunRecorderTest {

    @Mock
    private PipelineRunRepository pipelineRunRepository;

    @InjectMocks
    private PipelineRunRecorder recorder;

    private final LocalDateTime started = LocalDateTime.of(2026, 6, 30, 6, 0, 0);
    private final LocalDateTime finished = LocalDateTime.of(2026, 6, 30, 6, 1, 0);

    @Test
    void 수집_매칭_성공이면_INGEST_SUCCESS이고_카운트를_매핑한다() {
        recorder.recordIngest(started, finished,
                new CollectionResult(6, 5, 1, 0, 12),
                new MatchingResult(20, 8, 9));

        PipelineRun run = captureSaved();
        assertThat(run.getKind()).isEqualTo(RunKind.INGEST);
        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getCollectedTotal()).isEqualTo(6);
        assertThat(run.getCollectedNew()).isEqualTo(12);
        assertThat(run.getCollectFailed()).isEqualTo(1);
        assertThat(run.getMatchedArticles()).isEqualTo(8);
        assertThat(run.getTagsCreated()).isEqualTo(9);
        assertThat(run.getMembersNotified()).isZero();
    }

    @Test
    void 수집_매칭_한_단계_실패면_PARTIAL이다() {
        recorder.recordIngest(started, finished, new CollectionResult(6, 6, 0, 0, 10), null);

        assertThat(captureSaved().getStatus()).isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void 수집_매칭_전부_실패면_FAILED이다() {
        recorder.recordIngest(started, finished, null, null);

        assertThat(captureSaved().getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void 알림_성공이면_NOTIFICATION_SUCCESS이고_카운트를_매핑한다() {
        recorder.recordNotification(started, finished, new NotificationResult(3, 0, 14, 1));

        PipelineRun run = captureSaved();
        assertThat(run.getKind()).isEqualTo(RunKind.NOTIFICATION);
        assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
        assertThat(run.getMembersNotified()).isEqualTo(3);
        assertThat(run.getNotificationsRecorded()).isEqualTo(14);
        assertThat(run.getNotifyFailed()).isEqualTo(1);
        assertThat(run.getCollectedTotal()).isZero();
        assertThat(run.getMatchedArticles()).isZero();
    }

    @Test
    void 알림_단계_실패면_NOTIFICATION_FAILED이다() {
        recorder.recordNotification(started, finished, null);

        PipelineRun run = captureSaved();
        assertThat(run.getKind()).isEqualTo(RunKind.NOTIFICATION);
        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getMembersNotified()).isZero();
    }

    private PipelineRun captureSaved() {
        ArgumentCaptor<PipelineRun> captor = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepository).save(captor.capture());
        return captor.getValue();
    }
}
