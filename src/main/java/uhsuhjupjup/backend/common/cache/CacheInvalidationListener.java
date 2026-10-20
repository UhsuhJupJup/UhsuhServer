package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CacheInvalidationListener implements MessageListener, SubscriptionListener {

    private static final String UNKNOWN_CACHE = "unknown";

    private final Map<String, TieredCache> caches;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean subscribedBefore = new AtomicBoolean();

    public CacheInvalidationListener(List<TieredCache> caches, MeterRegistry meterRegistry) {
        this.caches = caches.stream().collect(Collectors.toUnmodifiableMap(TieredCache::getName, Function.identity()));
        this.meterRegistry = meterRegistry;
        this.caches.keySet().forEach(cacheName -> {
            received(cacheName, "processed");
            received(cacheName, "failed");
        });
        received(UNKNOWN_CACHE, "malformed");
        received(UNKNOWN_CACHE, "unknown_cache");
        for (String event : List.of("initial", "resubscribed", "unsubscribed")) {
            subscription(event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = body.indexOf(CacheInvalidationPublisher.SEPARATOR);
        if (separator <= 0) {
            received(UNKNOWN_CACHE, "malformed").increment();
            log.warn("알 수 없는 로컬 캐시 무효화 메시지 body={}", body);
            return;
        }
        String cacheName = body.substring(0, separator);
        TieredCache cache = caches.get(cacheName);
        if (cache == null) {
            received(UNKNOWN_CACHE, "unknown_cache").increment();
            return;
        }
        String key = body.substring(separator + CacheInvalidationPublisher.SEPARATOR.length());
        try {
            cache.invalidateLocal(key);
            received(cacheName, "processed").increment();
            log.info("로컬 캐시 무효화 수신 처리 cache={} key={}", cacheName, key);
        } catch (RuntimeException e) {
            received(cacheName, "failed").increment();
            log.warn("로컬 캐시 무효화 실패 body={} cause={}", body, e.toString());
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        caches.values().forEach(TieredCache::clearLocal);
        String event = subscribedBefore.getAndSet(true) ? "resubscribed" : "initial";
        subscription(event).increment();
        log.info("로컬 캐시 무효화 채널 구독({}), 놓친 메시지에 대비해 로컬 캐시를 비웠습니다 channel={}",
                event, new String(channel, StandardCharsets.UTF_8));
    }

    @Override
    public void onChannelUnsubscribed(byte[] channel, long count) {
        subscription("unsubscribed").increment();
        log.warn("로컬 캐시 무효화 채널 구독이 끊겼습니다 channel={}", new String(channel, StandardCharsets.UTF_8));
    }

    private Counter received(String cacheName, String result) {
        return Counter.builder("uhsuh.cache.invalidation.received").tag("cache", cacheName).tag("result", result)
                .register(meterRegistry);
    }

    private Counter subscription(String event) {
        return Counter.builder("uhsuh.cache.invalidation.subscriptions").tag("event", event).register(meterRegistry);
    }
}
