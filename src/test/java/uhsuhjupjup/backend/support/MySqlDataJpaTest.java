package uhsuhjupjup.backend.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import uhsuhjupjup.backend.config.JpaAuditingConfig;

/**
 * 리포지토리 슬라이스 테스트용 합성 애너테이션.
 *
 * <p>공유 MySQL 컨테이너의 격리된 DB(운영과 동일한 Flyway 스키마)에 붙고,
 * 엔티티는 {@code ddl-auto=validate}로 스키마와의 정합성까지 검증한다.
 * Flyway는 {@link SharedTestContainers}에서 직접 적용하므로 부트 자동 실행은 끈다.
 *
 * <pre>{@code
 * @MySqlDataJpaTest
 * class XxxRepositoryTest {
 *     @Autowired XxxRepository repository;
 * }
 * }</pre>
 *
 * 리포지토리 외의 협력 빈이 필요하면 {@code @Import}를 추가로 붙인다.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        SharedMySqlTestConfiguration.class,
        JpaAuditingConfig.class
})
public @interface MySqlDataJpaTest {
}
