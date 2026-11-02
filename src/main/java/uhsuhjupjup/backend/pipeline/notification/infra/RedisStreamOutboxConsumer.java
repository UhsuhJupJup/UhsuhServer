package uhsuhjupjup.backend.pipeline.notification.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageHandler;
import uhsuhjupjup.backend.pipeline.notification.application.OutboxMessageOutcome;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.stream.enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamOutboxConsumer implements StreamListener<String, MapRecord<String, String, String>>,
        SmartLifecycle {

    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redisTemplate;
    private final OutboxMessageHandler outboxMessageHandler;
    private final String consumerName;
    private final Duration pollTimeout;
    private final int batchSize;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public RedisStreamOutboxConsumer(RedisConnectionFactory connectionFactory,
                                     StringRedisTemplate redisTemplate,
                                     OutboxMessageHandler outboxMessageHandler,
                                     @Value("${app.instance-name:local}") String consumerName,
                                     @Value("${outbox.stream.poll-timeout:500ms}") Duration pollTimeout,
                                     @Value("${outbox.stream.batch-size:10}") int batchSize) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.outboxMessageHandler = outboxMessageHandler;
        this.consumerName = consumerName;
        this.pollTimeout = pollTimeout;
        this.batchSize = batchSize;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Long outboxId = outboxId(message);
        if (outboxId == null) {
            log.warn("아웃박스 ID가 없는 발송 메시지 정리 recordId={}", message.getId());
            acknowledge(message.getId());
            return;
        }
        OutboxMessageOutcome outcome = outboxMessageHandler.handle(outboxId);
        if (outcome == OutboxMessageOutcome.DONE) {
            acknowledge(message.getId());
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

    private static Long outboxId(MapRecord<String, String, String> message) {
        String value = message.getValue().get(OutboxStream.OUTBOX_ID_FIELD);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
