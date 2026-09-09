package uhsuhjupjup.backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 컨테이너를 JVM당 하나만 띄우고 공유한다(싱글턴 홀더).
 *
 * <p>MySQL은 컨테이너를 공유하되 스프링 컨텍스트마다 새 데이터베이스를 만들어 격리한다.
 * 각 새 DB에는 운영과 동일한 Flyway 마이그레이션({@code classpath:db/migration})을 직접 적용하므로,
 * 엔티티는 운영과 같은 {@code ddl-auto=validate}로 스키마 정합성까지 검증된다.
 */
public final class SharedTestContainers {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    private SharedTestContainers() {
    }

    /**
     * 공유 MySQL 컨테이너 안에 새 데이터베이스를 만들고 Flyway 마이그레이션까지 적용해 반환한다.
     */
    public static MySqlDatabase createMySqlDatabase() {
        MySqlDatabase database = createRawMySqlDatabase();
        migrate(database);

        return database;
    }

    public static MySqlDatabase createRawMySqlDatabase() {
        MySQLContainer<?> mysql = MySqlHolder.INSTANCE;
        String databaseName = "uhsuhjupjup_test_%d".formatted(DATABASE_SEQUENCE.incrementAndGet());

        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(),
                "root",
                mysql.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `%s`".formatted(databaseName));
            statement.execute("GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'%%'"
                    .formatted(databaseName, mysql.getUsername()));
        } catch (SQLException exception) {
            throw new IllegalStateException("테스트 데이터베이스를 생성할 수 없습니다.", exception);
        }

        String jdbcUrl = mysql.getJdbcUrl().replace(
                "/" + mysql.getDatabaseName(),
                "/" + databaseName
        );
        return new MySqlDatabase(jdbcUrl, mysql.getUsername(), mysql.getPassword());
    }

    private static void migrate(MySqlDatabase database) {
        Flyway.configure()
                .dataSource(database.jdbcUrl(), database.username(), database.password())
                .locations(MIGRATION_LOCATION)
                .load()
                .migrate();
    }

    public static GenericContainer<?> redis() {
        return RedisHolder.INSTANCE;
    }

    public record MySqlDatabase(String jdbcUrl, String username, String password) {
    }

    private static final class MySqlHolder {

        private static final MySQLContainer<?> INSTANCE = start();

        private static MySQLContainer<?> start() {
            MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0");
            container.start();
            return container;
        }
    }

    private static final class RedisHolder {

        private static final GenericContainer<?> INSTANCE = start();

        private static GenericContainer<?> start() {
            GenericContainer<?> container = new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(6379);
            container.start();
            return container;
        }
    }
}
