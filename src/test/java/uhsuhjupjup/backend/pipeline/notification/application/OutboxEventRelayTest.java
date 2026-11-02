package uhsuhjupjup.backend.pipeline.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
@Import({OutboxEventRelay.class, OutboxEventRelayTest.RecordingQueueConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventRelayTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordingQueue queue;

    @BeforeEach
    void setUp() {
        queue.published.clear();
    }

    @Test
    void 커밋되면_아웃박스_ID를_발행한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new OutboxCreatedEvent(7L)));

        assertThat(queue.published).containsExactly(7L);
    }

    @Test
    void 롤백되면_발행하지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new OutboxCreatedEvent(8L));
            status.setRollbackOnly();
        });

        assertThat(queue.published).isEmpty();
    }

    @Test
    void 커밋_전에는_발행하지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new OutboxCreatedEvent(9L));
            assertThat(queue.published).isEmpty();
        });

        assertThat(queue.published).containsExactly(9L);
    }

    @Test
    void 발행에_실패해도_예외를_밖으로_던지지_않는다() {
        queue.failing = true;
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    eventPublisher.publishEvent(new OutboxCreatedEvent(10L)));
        } finally {
            queue.failing = false;
        }

        assertThat(queue.published).isEmpty();
    }

    static class RecordingQueue implements OutboxMessageQueue {

        private final List<Long> published = new CopyOnWriteArrayList<>();
        private volatile boolean failing;

        @Override
        public void publish(Long outboxId) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            published.add(outboxId);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingQueueConfig {

        @Bean
        RecordingQueue recordingQueue() {
            return new RecordingQueue();
        }
    }
}
