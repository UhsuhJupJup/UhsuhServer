package uhsuhjupjup.backend.pipeline.notification.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageHandler;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageOutcome;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.stream.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamOutboxConsumer implements StreamListener<String, MapRecord<String, String, String>>,
        SmartLifecycle {

    private static final int RECLAIM_PAGE_SIZE = 100;
    private static final int RECLAIM_SCAN_LIMIT = 1000;

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final OutboxMessageHandler outboxMessageHandler;
    private final String consumerName;
    private final Duration pollTimeout;
    private final int batchSize;
    private final Duration reclaimMinIdle;
    private final int maxDeliveries;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public RedisStreamOutboxConsumer(RedisConnectionFactory connectionFactory,
                                     StringRedisTemplate redisTemplate,
                                     OutboxMessageHandler outboxMessageHandler,
                                     @Value("${app.instance-name:local}") String consumerName,
                                     @Value("${outbox.stream.poll-timeout:500ms}") Duration pollTimeout,
                                     @Value("${outbox.stream.batch-size:10}") int batchSize,
                                     @Value("${outbox.stream.reclaim-min-idle:PT5M}") Duration reclaimMinIdle,
                                     @Value("${outbox.stream.max-deliveries:10}") int maxDeliveries) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.outboxMessageHandler = outboxMessageHandler;
        this.consumerName = consumerName;
        this.pollTimeout = pollTimeout;
        this.batchSize = batchSize;
        this.reclaimMinIdle = reclaimMinIdle;
        this.maxDeliveries = maxDeliveries;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        process(message.getId(), message.getValue());
    }

    @Scheduled(fixedDelayString = "${outbox.stream.reclaim-delay-ms:60000}",
            initialDelayString = "${outbox.stream.reclaim-delay-ms:60000}")
    public void reclaimStale() {
        if (!isRunning()) {
            return;
        }
        try {
            int reclaimed = reclaim();
            if (reclaimed > 0) {
                log.info("완료되지 않은 발송 메시지 회수={} consumer={}", reclaimed, consumerName);
            }
        } catch (RuntimeException e) {
            log.warn("완료되지 않은 발송 메시지 회수 실패 사유={}", e.getMessage());
        }
    }

    int reclaim() {
        int reclaimed = 0;
        int scanned = 0;
        Range<String> range = Range.unbounded();
        while (scanned < RECLAIM_SCAN_LIMIT) {
            PendingMessages page = redisTemplate.opsForStream()
                    .pending(OutboxStream.KEY, OutboxStream.GROUP, range, RECLAIM_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (PendingMessage pending : page) {
                scanned++;
                if (pending.getElapsedTimeSinceLastDelivery().compareTo(reclaimMinIdle) >= 0 && reclaim(pending)) {
                    reclaimed++;
                }
            }
            if (page.size() < RECLAIM_PAGE_SIZE) {
                break;
            }
            range = Range.rightUnbounded(Range.Bound.exclusive(page.get(page.size() - 1).getIdAsString()));
        }
        return reclaimed;
    }

    private boolean reclaim(PendingMessage pending) {
        List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
                .claim(OutboxStream.KEY, OutboxStream.GROUP, consumerName, reclaimMinIdle, pending.getId());
        if (claimed.isEmpty()) {
            return false;
        }
        MapRecord<String, Object, Object> record = claimed.get(0);
        if (pending.getTotalDeliveryCount() >= maxDeliveries) {
            log.warn("재전달 상한을 넘은 발송 메시지 정리, 미완료면 복구 폴러가 다시 발행 recordId={} deliveries={} value={}",
                    record.getId(), pending.getTotalDeliveryCount(), record.getValue());
            acknowledge(record.getId());
            return true;
        }
        process(record.getId(), record.getValue());
        return true;
    }

    private void process(RecordId recordId, Map<?, ?> value) {
        Long outboxId = outboxId(value);
        if (outboxId == null) {
            log.warn("아웃박스 ID가 없는 발송 메시지 정리 recordId={}", recordId);
            acknowledge(recordId);
            return;
        }
        OutboxMessageOutcome outcome = outboxMessageHandler.handle(outboxId);
        if (outcome == OutboxMessageOutcome.DONE) {
            acknowledge(recordId);
        }
    }

    @Override
    public synchronized void start() {
        if (container != null) {
            return;
        }
        createGroup();
        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(pollTimeout)
                        .batchSize(batchSize)
                        .errorHandler(this::onError)
                        .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.register(StreamReadRequest.builder(StreamOffset.create(OutboxStream.KEY, ReadOffset.lastConsumed()))
                .consumer(Consumer.from(OutboxStream.GROUP, consumerName))
                .autoAcknowledge(false)
                .cancelOnError(error -> false)
                .build(), this);
        container.start();
        log.info("아웃박스 발송 메시지 소비 시작 group={} consumer={}", OutboxStream.GROUP, consumerName);
    }

    @Override
    public synchronized void stop() {
        if (container == null) {
            return;
        }
        container.stop();
        container = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return container != null && container.isRunning();
    }

    private void acknowledge(RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(OutboxStream.KEY, OutboxStream.GROUP, recordId);
        redisTemplate.opsForStream().delete(OutboxStream.KEY, recordId);
    }

    private void createGroup() {
        try {
            redisTemplate.opsForStream().createGroup(OutboxStream.KEY, ReadOffset.from("0"), OutboxStream.GROUP);
        } catch (RedisSystemException e) {
            if (!isBusyGroup(e)) {
                log.warn("아웃박스 발송 메시지 그룹 생성 실패 사유={}", e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("아웃박스 발송 메시지 그룹 생성 실패 사유={}", e.getMessage());
        }
    }

    private void onError(Throwable error) {
        log.warn("아웃박스 발송 메시지 읽기 실패 사유={}", error.getMessage());
        if (String.valueOf(rootMessage(error)).contains("NOGROUP")) {
            createGroup();
        }
    }

    private static boolean isBusyGroup(Throwable error) {
        return String.valueOf(rootMessage(error)).contains("BUSYGROUP");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static Long outboxId(Map<?, ?> value) {
        Object outboxId = value.get(OutboxStream.OUTBOX_ID_FIELD);
        if (outboxId == null) {
            return null;
        }
        try {
            return Long.valueOf(outboxId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
