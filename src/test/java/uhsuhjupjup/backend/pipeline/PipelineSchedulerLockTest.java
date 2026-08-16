package uhsuhjupjup.backend.pipeline;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uhsuhjupjup.backend.learningnote.application.GlobalKeywordGraphProvider;
import uhsuhjupjup.backend.pipeline.collection.application.CollectionService;
import uhsuhjupjup.backend.pipeline.matching.application.MatchingService;
import uhsuhjupjup.backend.pipeline.notification.application.NotificationService;
import uhsuhjupjup.backend.pipeline.run.application.PipelineRunRecorder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 분산락이 다중 인스턴스의 스케줄러 중복 실행을 막는지 검증.
 * 실제 Redis(Testcontainers)에 두 개의 RedisLockRegistry(= 두 인스턴스)를 붙여 동시 실행시킨다.
 */
@Testcontainers
class PipelineSchedulerLockTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void startRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
    }

    @Test
    void 두_인스턴스가_동시에_ingest해도_수집은_한_번만_실행된다() throws InterruptedException {
        AtomicInteger collectRuns = new AtomicInteger();

        PipelineScheduler instanceA = newInstance(collectRuns);
        PipelineScheduler instanceB = newInstance(collectRuns);

        CountDownLatch gate = new CountDownLatch(1);
        Thread a = new Thread(() -> { awaitGate(gate); instanceA.ingest(); });
        Thread b = new Thread(() -> { awaitGate(gate); instanceB.ingest(); });

        a.start();
        b.start();
        gate.countDown();          // 두 인스턴스 동시 출발
        a.join();
        b.join();

        // 락이 없거나 스킵 로직이 깨졌으면 2가 된다.
        assertThat(collectRuns.get()).isEqualTo(1);
    }

    @Test
    void 앞_실행이_끝나면_다음_실행은_다시_락을_획득한다() {
        AtomicInteger collectRuns = new AtomicInteger();
        PipelineScheduler scheduler = newInstance(collectRuns);

        scheduler.ingest();
        scheduler.ingest();

        // 매번 unlock 되어 다음 실행이 정상 획득 → 2회
        assertThat(collectRuns.get()).isEqualTo(2);
    }

    private PipelineScheduler newInstance(AtomicInteger collectRuns) {
        CollectionService collection = mock(CollectionService.class);
        MatchingService matching = mock(MatchingService.class);
        NotificationService notification = mock(NotificationService.class);
        PipelineRunRecorder recorder = mock(PipelineRunRecorder.class);

        given(collection.collectAll()).willAnswer(invocation -> {
            collectRuns.incrementAndGet();
            Thread.sleep(500);     // 락을 쥔 채 붙잡아, 다른 인스턴스의 tryLock이 그 사이 실패하도록
            return null;
        });

        GlobalKeywordGraphProvider graphProvider = mock(GlobalKeywordGraphProvider.class);
        RedisLockRegistry registry = new RedisLockRegistry(connectionFactory, "uhsuh-lock", 60_000L);
        return new PipelineScheduler(collection, matching, notification, recorder, registry, graphProvider);
    }

    private void awaitGate(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
