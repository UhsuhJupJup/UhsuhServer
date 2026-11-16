package uhsuhjupjup.backend.learningnote.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uhsuhjupjup.backend.learningnote.domain.NoteAnalysisRetryPolicy;

import java.time.Duration;

@Configuration
class NoteAnalysisJobConfig {

    @Bean
    NoteAnalysisRetryPolicy noteAnalysisRetryPolicy(
            @Value("${note.analysis.retry.base-delay:PT30S}") Duration baseDelay,
            @Value("${note.analysis.retry.max-delay:PT15M}") Duration maxDelay,
            @Value("${note.analysis.retry.horizon:PT6H}") Duration horizon,
            @Value("${note.analysis.retry.max-attempts:20}") int maxAttempts,
            @Value("${note.analysis.retry.circuit-open-delay:PT30S}") Duration circuitOpenDelay) {
        return new NoteAnalysisRetryPolicy(baseDelay, maxDelay, horizon, maxAttempts, circuitOpenDelay);
    }
}
