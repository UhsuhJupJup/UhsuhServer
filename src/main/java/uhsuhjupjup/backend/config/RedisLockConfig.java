package uhsuhjupjup.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

@Configuration
public class RedisLockConfig {

    @Bean
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory rcf) {
        return new RedisLockRegistry(rcf, "uhsuh-lock", 1_800_000L);
    }
}
