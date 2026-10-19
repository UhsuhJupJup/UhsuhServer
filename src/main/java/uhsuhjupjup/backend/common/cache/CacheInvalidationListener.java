package uhsuhjupjup.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CacheInvalidationListener implements MessageListener, SubscriptionListener {

    private final Map<String, TieredCache> caches;

    public CacheInvalidationListener(List<TieredCache> caches) {
        this.caches = caches.stream().collect(Collectors.toUnmodifiableMap(TieredCache::getName, Function.identity()));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = body.indexOf(CacheInvalidationPublisher.SEPARATOR);
        if (separator <= 0) {
            log.warn("알 수 없는 로컬 캐시 무효화 메시지 body={}", body);
            return;
        }
        TieredCache cache = caches.get(body.substring(0, separator));
        if (cache == null) {
            return;
        }
        try {
            cache.invalidateLocal(body.substring(separator + CacheInvalidationPublisher.SEPARATOR.length()));
        } catch (RuntimeException e) {
            log.warn("로컬 캐시 무효화 실패 body={} cause={}", body, e.toString());
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        caches.values().forEach(TieredCache::clearLocal);
        log.info("로컬 캐시 무효화 채널 구독, 놓친 메시지에 대비해 로컬 캐시를 비웠습니다 channel={}",
                new String(channel, StandardCharsets.UTF_8));
    }
}
