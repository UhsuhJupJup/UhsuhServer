package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.pipeline.notification.application.dto.OutboxStatusSummary;
import uhsuhjupjup.backend.pipeline.notification.domain.NotificationOutbox;
import uhsuhjupjup.backend.pipeline.notification.domain.OutboxStatus;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.pipeline.notification.infra.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxAdminServiceTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @InjectMocks
    private OutboxAdminService outboxAdminService;

    @Test
    void summary는_상태별_카운트와_실패목록을_반환한다() {
        given(notificationOutboxRepository.countByStatus(OutboxStatus.PENDING)).willReturn(3L);
        given(notificationOutboxRepository.countByStatus(OutboxStatus.SENT)).willReturn(10L);
        given(notificationOutboxRepository.countByStatus(OutboxStatus.FAILED)).willReturn(1L);
        NotificationOutbox failed = outbox();
        given(notificationOutboxRepository.findByStatusOrderByCreatedAtDesc(eq(OutboxStatus.FAILED),
                any(Pageable.class))).willReturn(List.of(failed));

        OutboxStatusSummary summary = outboxAdminService.summary(50);

        assertThat(summary.pending()).isEqualTo(3);
        assertThat(summary.sent()).isEqualTo(10);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.failedEntries()).containsExactly(failed);
    }

    @Test
    void requeue는_행을_PENDING으로_되돌린다() {
        NotificationOutbox row = outbox();
        row.markFailed("boom");
        given(notificationOutboxRepository.findById(1L)).willReturn(Optional.of(row));

        outboxAdminService.requeue(1L);

        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void requeue_대상이_없으면_예외를_던진다() {
        given(notificationOutboxRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> outboxAdminService.requeue(99L))
                .isInstanceOf(BusinessException.class);
    }

    private NotificationOutbox outbox() {
        return NotificationOutbox.pending("a@test.com", RecipientType.MEMBER, 2, "제목", "<html>",
                null, LocalDateTime.now());
    }
}
