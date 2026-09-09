package uhsuhjupjup.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * MySQL과 Redis가 모두 필요한 통합 테스트용 설정.
 *
 * <p>{@link SharedMySqlTestConfiguration}(격리 DB)에 더해 공유 Redis 컨테이너를 연결한다.
 */
@Import(SharedMySqlTestConfiguration.class)
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestConfiguration {

    @Bean
    DynamicPropertyRegistrar redisProperties() {
        GenericContainer<?> redis = SharedTestContainers.redis();
        return registry -> {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        };
    }
}
