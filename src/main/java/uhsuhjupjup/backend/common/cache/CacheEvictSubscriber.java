package uhsuhjupjup.backend.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

@Slf4j
public class CacheEvictSubscriber implements MessageListener {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final String selfSenderId;

    public CacheEvictSubscriber(CacheManager cacheManager, ObjectMapper objectMapper, CacheEvictPublisher publisher) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.selfSenderId = publisher.senderId();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        CacheEvictMessage evict = parse(message);
        if (evict == null) {
            return;
        }
        if (selfSenderId.equals(evict.senderId())) {
            return;
        }
        Cache cache = cacheManager.getCache(evict.cacheName());
        if (!(cache instanceof TwoLevelCache twoLevel)) {
            return;
        }
        if (evict.key() == null) {
            twoLevel.evictLocalAll();
        } else {
            twoLevel.evictLocal(evict.key());
        }
    }

    private CacheEvictMessage parse(Message message) {
        try {
            return objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), CacheEvictMessage.class);
        } catch (Exception e) {
            log.warn("캐시 무효화 메시지 파싱 실패", e);
            return null;
        }
    }
}
