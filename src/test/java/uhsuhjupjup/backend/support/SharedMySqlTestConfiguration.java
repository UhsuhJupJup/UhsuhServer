package uhsuhjupjup.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 스프링 컨텍스트마다 격리된 MySQL 데이터베이스를 연결한다.
 *
 * <p>Flyway 마이그레이션은 {@link SharedTestContainers#createMySqlDatabase()}에서 이미 적용하므로
 * 스프링 부트의 Flyway 자동 실행은 끈다(이중 마이그레이션 방지). 컨텍스트가 여러 개 떠도
 * 공유 컨테이너의 커넥션을 고갈시키지 않도록 커넥션 풀을 작게 잡는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SharedMySqlTestConfiguration {

    @Bean
    DynamicPropertyRegistrar mysqlProperties() {
        SharedTestContainers.MySqlDatabase database = SharedTestContainers.createMySqlDatabase();
        return registry -> {
            registry.add("spring.datasource.url", database::jdbcUrl);
            registry.add("spring.datasource.username", database::username);
            registry.add("spring.datasource.password", database::password);
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
            registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
            registry.add("spring.flyway.enabled", () -> false);
        };
    }
}
