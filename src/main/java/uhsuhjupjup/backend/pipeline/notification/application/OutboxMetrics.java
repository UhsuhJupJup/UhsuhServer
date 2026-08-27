package uhsuhjupjup.backend.pipeline.notification.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void bindMetrics() {
        Gauge.builder("notification.outbox.pending", notificationOutboxRepository,
                        repository -> repository.countByStatus(OutboxStatus.PENDING))
                .description("발송 대기 중인 아웃박스 행 수")
                .register(meterRegistry);
        Gauge.builder("notification.outbox.failed", notificationOutboxRepository,
                        repository -> repository.countByStatus(OutboxStatus.FAILED))
                .description("최대 재시도 초과로 격리(dead-letter)된 아웃박스 행 수")
                .register(meterRegistry);
    }
}
