package uhsuhjupjup.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import uhsuhjupjup.backend.common.cache.CacheEvictPublisher;
import uhsuhjupjup.backend.common.cache.CacheEvictSubscriber;

@Configuration
@Profile("prod")
public class CachePubSubConfig {

    @Bean
    public CacheEvictSubscriber cacheEvictSubscriber(CacheManager cacheManager,
                                                     ObjectMapper objectMapper,
                                                     CacheEvictPublisher publisher) {
        return new CacheEvictSubscriber(cacheManager, objectMapper, publisher);
    }

    @Bean
    public RedisMessageListenerContainer cacheEvictListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     CacheEvictSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(CacheEvictPublisher.CHANNEL));
        return container;
    }
}
