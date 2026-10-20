package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.LOCAL;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.LOCK;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.cacheKey;
import static uhsuhjupjup.backend.common.cache.CacheTestServers.lockKey;

class CacheMetricsTest {

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
    void L1_미스에서_적중으로_바뀌면_L2_조회와_집계는_처음_한_번만_센다() {
        Server server = servers.server();

        server.cache().get(1L, () -> "graph-1");
        server.cache().get(1L, () -> "never");
        server.cache().get(1L, () -> "never");

        assertThat(server.count("uhsuh.cache.local.gets", "miss")).isEqualTo(1.0);
        assertThat(server.count("uhsuh.cache.local.gets", "hit")).isEqualTo(2.0);
        assertThat(server.remoteLookups()).isEqualTo(1.0);
        assertThat(server.count("cache.gets", "miss")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.loads", "source", "read", "result", "success")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.local.loads", "role", "leader")).isEqualTo(1.0);
        assertThat(server.count("uhsuh.cache.local.puts", "stored")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.redis.commands", "command", "lock", "result", "success")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.redis.commands", "command", "store_if_owner", "result", "success")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.redis.commands", "command", "release", "result", "success")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.lock.wait", "purpose", "read", "result", "acquired")).isEqualTo(1.0);
        assertThat(server.registry().get("uhsuh.cache.local.size").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 다른_서버가_채운_값을_읽으면_L2_적중과_L1_저장만_늘고_집계는_늘지_않는다() {
        Server first = servers.server();
        Server second = servers.server();
        first.cache().get(2L, () -> "graph-2");

        second.cache().get(2L, () -> "never");

        assertThat(second.count("cache.gets", "hit")).isEqualTo(1.0);
        assertThat(second.loads()).isZero();
        assertThat(second.count("uhsuh.cache.local.puts", "stored")).isEqualTo(1.0);
        assertThat(second.tagged("uhsuh.cache.lock.attempts", "purpose", "read")).isZero();
    }

    @Test
    void 같은_키에_요청이_몰리면_대표_한_건과_대기_건수와_집계_한_번이_남는다() throws Exception {
        Server server = servers.server();
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            Thread.sleep(300);
            return "graph-3";
        };
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> { start.await(); return server.cache().get(3L, loader); }));
        }
        start.countDown();
        for (Future<String> result : results) {
            result.get(10, TimeUnit.SECONDS);
        }

        assertThat(server.count("uhsuh.cache.local.gets", "miss")).isEqualTo(8.0);
        assertThat(server.tagged("uhsuh.cache.local.loads", "role", "leader")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.local.loads", "role", "follower")).isEqualTo(7.0);
        assertThat(server.loads()).isEqualTo(1.0);
        assertThat(server.remoteLookups()).isEqualTo(1.0);
    }

    @Test
    void 락을_기다리다_다른_서버의_값을_찾거나_시간이_지나면_대기_결과와_DB_우회를_남긴다() throws Exception {
        Server server = servers.server(new CacheLockSettings(Duration.ofSeconds(10), Duration.ofMillis(300),
                Duration.ofMillis(20), Duration.ofSeconds(10)));
        redis.opsForValue().set(lockKey(4L), "other-server", Duration.ofSeconds(30));
        Future<String> waiting = pool.submit(() -> server.cache().get(4L, () -> "computed"));
        server.awaitLocalMisses(1);
        Thread.sleep(100);
        redis.opsForValue().set(cacheKey(4L), "\"stored-by-other\"", Duration.ofMinutes(5));
        waiting.get(10, TimeUnit.SECONDS);

        redis.opsForValue().set(lockKey(5L), "stuck-server", Duration.ofSeconds(30));
        server.cache().get(5L, () -> "graph-5");

        assertThat(server.timed("uhsuh.cache.lock.wait", "purpose", "read", "result", "value_found")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.lock.wait", "purpose", "read", "result", "timeout")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.fallbacks", "reason", "lock_timeout")).isEqualTo(1.0);
        assertThat(server.timed("uhsuh.cache.redis.commands", "command", "store_if_absent", "result", "success")).isEqualTo(1.0);
    }

    @Test
    void Redis가_멈추면_명령_오류와_건너뜀과_DB_우회를_원인별로_센다() {
        Server server = servers.server(LOCK, LOCAL, servers.connectionFactory("127.0.0.1", 1), CacheTestServers.serializer());

        server.cache().get(6L, () -> "graph-6");
        server.cache().get(7L, () -> "graph-7");

        assertThat(server.timed("uhsuh.cache.redis.commands", "command", "get", "result", "error")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.redis.skipped", "command", "get")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.redis.skipped", "command", "lock")).isEqualTo(2.0);
        assertThat(server.tagged("uhsuh.cache.fallbacks", "reason", "redis_skipped")).isEqualTo(2.0);
        assertThat(server.loads()).isEqualTo(2.0);
        assertThat(server.count("uhsuh.cache.local.puts", "stored")).isEqualTo(2.0);
    }

    @Test
    void 집계가_실패하면_실패한_집계로_센다() {
        Server server = servers.server();

        assertThatThrownBy(() -> server.cache().get(8L, () -> { throw new IllegalStateException("집계 실패"); }));

        assertThat(server.timed("uhsuh.cache.loads", "source", "read", "result", "failure")).isEqualTo(1.0);
        assertThat(server.loads()).isZero();
    }

    @Test
    void 갱신은_집계를_갱신으로_나눠_세고_발행과_서버마다의_수신과_처리를_따로_센다() throws Exception {
        Server first = servers.server();
        Server second = servers.server();
        servers.listen(first);
        servers.listen(second);
        first.cache().get(9L, () -> "old");
        second.cache().get(9L, () -> "never");

        first.cache().refresh(9L, () -> "new");
        first.awaitLocalEmpty(9L);
        second.awaitLocalEmpty(9L);

        assertThat(first.timed("uhsuh.cache.loads", "source", "refresh", "result", "success")).isEqualTo(1.0);
        assertThat(first.timed("uhsuh.cache.loads", "source", "read", "result", "success")).isEqualTo(1.0);
        assertThat(first.count("uhsuh.cache.refreshes", "stored")).isEqualTo(1.0);
        assertThat(first.count("uhsuh.cache.invalidation.published", "success")).isEqualTo(1.0);
        assertThat(second.count("uhsuh.cache.invalidation.published", "success")).isZero();
        for (Server server : List.of(first, second)) {
            assertThat(server.count("uhsuh.cache.invalidation.received", "processed")).isEqualTo(1.0);
            assertThat(server.invalidations("message")).isEqualTo(1.0);
        }
    }

    @Test
    void 깨진_메시지와_모르는_캐시의_메시지는_처리하지_않고_따로_센다() throws Exception {
        Server server = servers.server();
        servers.listen(server);
        server.cache().get(10L, () -> "graph-10");

        redis.convertAndSend(CacheInvalidationPublisher.CHANNEL, "no-separator");
        redis.convertAndSend(CacheInvalidationPublisher.CHANNEL, "otherCache::10");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.tagged("uhsuh.cache.invalidation.received", "cache", "unknown") < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(server.count("uhsuh.cache.invalidation.received", "malformed")).isEqualTo(1.0);
        assertThat(server.count("uhsuh.cache.invalidation.received", "unknown_cache")).isEqualTo(1.0);
        assertThat(server.count("uhsuh.cache.invalidation.received", "processed")).isZero();
        assertThat(server.cache().localValue(10L)).isEqualTo("graph-10");
    }

    @Test
    void 발행이_실패하면_실패로_센다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StringRedisTemplate dead = new StringRedisTemplate(servers.connectionFactory("127.0.0.1", 1));
        dead.afterPropertiesSet();

        new CacheInvalidationPublisher(dead, registry).publishInvalidation(CacheTestServers.NAME, 11L);

        assertThat(registry.get("uhsuh.cache.invalidation.published").tag("result", "failure").counter().count()).isEqualTo(1.0);
    }

    @Test
    void 구독이_끊겼다가_다시_붙으면_처음_구독과_재구독과_L1_비움을_따로_센다() throws Exception {
        Server server = servers.server();
        servers.listen(server);
        assertThat(server.tagged("uhsuh.cache.invalidation.subscriptions", "event", "initial")).isEqualTo(1.0);
        server.cache().get(12L, () -> "graph-12");

        servers.killSubscriberConnections();
        server.awaitLocalEmpty(12L);
        servers.awaitSubscribers(1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.tagged("uhsuh.cache.invalidation.subscriptions", "event", "resubscribed") < 1 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(server.tagged("uhsuh.cache.invalidation.subscriptions", "event", "resubscribed")).isEqualTo(1.0);
        assertThat(server.tagged("uhsuh.cache.local.clears", "reason", "resubscribe")).isEqualTo(2.0);
        assertThat(server.tagged("uhsuh.cache.invalidation.subscriptions", "event", "unsubscribed")).isZero();
    }
}
