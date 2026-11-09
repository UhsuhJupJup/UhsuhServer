package uhsuhjupjup.backend.learningnote.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "note.analysis.poller.enabled", havingValue = "true", matchIfMissing = true)
class NoteAnalysisJobPoller {

    private final NoteAnalysisJobRunner runner;

    @Scheduled(fixedDelayString = "${note.analysis.poller.delay-ms:5000}",
            initialDelayString = "${note.analysis.poller.initial-delay-ms:30000}")
    public void poll() {
        try {
            runner.dispatch(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("노트 분석 작업 폴링 실패 사유={}", e.toString());
        }
    }
}
