package uhsuhjupjup.backend.common.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import uhsuhjupjup.backend.common.cache.CacheTestServers.Server;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.LOCAL;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.LOCK;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.cacheKey;

class TieredCacheLayersTest {

    private static final GenericContainer<?> REDIS = SharedTestContainers.redis();

    private final ExecutorService pool = Executors.newFixedThreadPool(16);
    private CacheTestServers servers;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        servers = new CacheTestServers(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = servers.redis();
        Set<String> keys = redis.keys("test:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        servers.close();
    }

    @Test
    void 로컬에_있으면_Redis와_DB를_부르지_않는다() {
        Server server = servers.server();
        server.cache().get(1L, () -> "graph-1");
        double lookups = server.remoteLookups();
        AtomicInteger computed = new AtomicInteger();

        String value = server.cache().get(1L, () -> "computed-" + computed.incrementAndGet());

        assertThat(value).isEqualTo("graph-1");
        assertThat(computed).hasValue(0);
        assertThat(server.remoteLookups()).isEqualTo(lookups);
        assertThat(server.count("uhsuh.cache.local.gets", "hit")).isEqualTo(1.0);
    }

    @Test
    void 로컬에_없고_Redis에_있으면_DB를_부르지_않고_로컬을_채운다() {
        Server server = servers.server();
        redis.opsForValue().set(cacheKey(2L), "\"from-redis\"", Duration.ofMinutes(5));
        AtomicInteger computed = new AtomicInteger();

        String value = server.cache().get(2L, () -> "computed-" + computed.incrementAndGet());

        assertThat(value).isEqualTo("from-redis");
        assertThat(computed).hasValue(0);
        assertThat(server.cache().localValue(2L)).isEqualTo("from-redis");
        assertThat(server.count("uhsuh.cache.lock.attempts", "acquired")).isZero();
    }

    @Test
    void 둘_다_없으면_분산_락을_거쳐_DB에서_계산하고_Redis와_로컬을_채운다() {
        Server server = servers.server();

        String value = server.cache().get(3L, () -> "graph-3");

        assertThat(value).isEqualTo("graph-3");
        assertThat(server.loads()).isEqualTo(1.0);
        assertThat(server.count("uhsuh.cache.lock.attempts", "acquired")).isEqualTo(1.0);
        assertThat(redis.opsForValue().get(cacheKey(3L))).isEqualTo("\"graph-3\"");
        assertThat(server.cache().localValue(3L)).isEqualTo("graph-3");
    }

    @Test
    void Redis가_멈춰도_로컬에_있으면_Redis와_DB_없이_바로_응답한다() {
        GenericContainer<?> ownRedis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        ownRedis.start();
        try {
            LettuceConnectionFactory factory = servers.connectionFactory(ownRedis.getHost(), ownRedis.getMappedPort(6379));
            Server server = servers.server(LOCK, LOCAL, factory, CacheTestServers.serializer());
            server.cache().get(4L, () -> "graph-4");
            ownRedis.stop();
            AtomicInteger computed = new AtomicInteger();

            long started = System.nanoTime();
            String value = server.cache().get(4L, () -> "computed-" + computed.incrementAndGet());

            assertThat(value).isEqualTo("graph-4");
            assertThat(computed).hasValue(0);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(200));
        } finally {
            if (ownRedis.isRunning()) {
                ownRedis.stop();
            }
        }
    }

    @Test
    void Redis가_멈추고_로컬에도_없으면_같은_서버의_같은_키는_한_번만_DB에서_계산하고_이후엔_로컬을_쓴다() throws Exception {
        Server server = servers.server(LOCK, LOCAL, servers.connectionFactory("127.0.0.1", 1), CacheTestServers.serializer());
        AtomicInteger computed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            Thread.sleep(300);
            return "graph-5";
        };

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> { start.await(); return server.cache().get(5L, loader); }));
        }
        start.countDown();
        for (Future<String> result : results) {
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("graph-5");
        }
        String later = server.cache().get(5L, loader);

        assertThat(later).isEqualTo("graph-5");
        assertThat(computed).hasValue(1);
        assertThat(server.cache().localValue(5L)).isEqualTo("graph-5");
    }

    @Test
    void Redis가_멈추면_서버마다의_single_flight는_서로_독립이라_서버_수만큼_계산한다() throws Exception {
        Server first = servers.server(LOCK, LOCAL, servers.connectionFactory("127.0.0.1", 1), CacheTestServers.serializer());
        Server second = servers.server(LOCK, LOCAL, servers.connectionFactory("127.0.0.1", 1), CacheTestServers.serializer());
        AtomicInteger computed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            Thread.sleep(300);
            return "graph-6";
        };

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(pool.submit(() -> { start.await(); return first.cache().get(6L, loader); }));
            results.add(pool.submit(() -> { start.await(); return second.cache().get(6L, loader); }));
        }
        start.countDown();
        for (Future<String> result : results) {
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("graph-6");
        }

        assertThat(computed).hasValue(2);
    }

    @Test
    void 로컬_TTL이_지나면_Redis의_새_값을_읽는다() {
        Server server = servers.server();
        server.cache().get(7L, () -> "old-graph");
        redis.opsForValue().set(cacheKey(7L), "\"new-graph\"", Duration.ofMinutes(50));

        server.advance(Duration.ofSeconds(269));
        String beforeExpiry = server.cache().get(7L, () -> "computed");
        server.advance(Duration.ofSeconds(32));
        String afterExpiry = server.cache().get(7L, () -> "computed");

        assertThat(beforeExpiry).isEqualTo("old-graph");
        assertThat(afterExpiry).isEqualTo("new-graph");
        assertThat(server.cache().localValue(7L)).isEqualTo("new-graph");
    }

    @Test
    void 조회해도_로컬_TTL이_늘어나지_않는다() {
        Server server = servers.server(LOCK, new LocalCacheSettings(Duration.ofMinutes(5), 1_000),
                servers.connectionFactory(), CacheTestServers.serializer());
        server.cache().get(8L, () -> "graph-8");
        redis.opsForValue().set(cacheKey(8L), "\"new-graph\"", Duration.ofMinutes(50));

        for (int i = 0; i < 4; i++) {
            server.advance(Duration.ofMinutes(1));
            assertThat(server.cache().get(8L, () -> "computed")).isEqualTo("graph-8");
        }
        server.advance(Duration.ofSeconds(61));

        assertThat(server.cache().get(8L, () -> "computed")).isEqualTo("new-graph");
    }
}
