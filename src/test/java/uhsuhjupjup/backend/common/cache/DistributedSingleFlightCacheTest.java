package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistributedSingleFlightCacheTest {

    private static final GenericContainer<?> REDIS = SharedTestContainers.redis();
    private static final String NAME = "graphTest";
    private static final String CACHE_PREFIX = "test:cache:";
    private static final String LOCK_PREFIX = "test:lock:";
    private static final TtlJitter TTL = new TtlJitter(Duration.ofMinutes(55), Duration.ofMinutes(60));
    private static final CacheLockSettings LOCK = new CacheLockSettings(
            Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofSeconds(10));

    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(16);
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis = new StringRedisTemplate(connectionFactory());
        redis.afterPropertiesSet();
        Set<String> keys = redis.keys("test:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        factories.forEach(LettuceConnectionFactory::destroy);
    }

    @Test
    void 두_서버가_같은_키를_동시에_놓쳐도_집계는_한_번만_하고_결과를_함께_쓴다() throws Exception {
        Server first = server(LOCK);
        Server second = server(LOCK);
        AtomicInteger computed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            Thread.sleep(300);
            return "graph-1";
        };

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> { start.await(); return first.cache.get(1L, loader); }));
            results.add(pool.submit(() -> { start.await(); return second.cache.get(1L, loader); }));
        }
        start.countDown();

        for (Future<String> result : results) {
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("graph-1");
        }
        assertThat(computed).hasValue(1);
        assertThat(redis.opsForValue().get(cacheKey(1L))).isEqualTo("\"graph-1\"");
        assertThat(redis.hasKey(lockKey(1L))).isFalse();
    }

    @Test
    void 같은_서버_안의_동시_요청은_락을_한_번만_시도하고_결과를_나눠_쓴다() throws Exception {
        Server server = server(LOCK);
        AtomicInteger computed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            Thread.sleep(300);
            return "graph-2";
        };

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> { start.await(); return server.cache.get(2L, loader); }));
        }
        start.countDown();

        for (Future<String> result : results) {
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("graph-2");
        }
        assertThat(computed).hasValue(1);
        assertThat(server.count("uhsuh.cache.lock.attempts", "acquired")).isEqualTo(1.0);
    }

    @Test
    void 서로_다른_키는_서버가_달라도_동시에_계산한다() throws Exception {
        Server first = server(LOCK);
        Server second = server(LOCK);
        CyclicBarrier bothInside = new CyclicBarrier(2);
        Callable<String> keyThree = () -> {
            bothInside.await(5, TimeUnit.SECONDS);
            return "graph-3";
        };
        Callable<String> keyFour = () -> {
            bothInside.await(5, TimeUnit.SECONDS);
            return "graph-4";
        };

        Future<String> three = pool.submit(() -> first.cache.get(3L, keyThree));
        Future<String> four = pool.submit(() -> second.cache.get(4L, keyFour));

        assertThat(three.get(10, TimeUnit.SECONDS)).isEqualTo("graph-3");
        assertThat(four.get(10, TimeUnit.SECONDS)).isEqualTo("graph-4");
    }

    @Test
    void 락을_얻은_뒤_캐시를_다시_확인해_이미_있으면_계산하지_않는다() throws Exception {
        Server server = server(new CacheLockSettings(Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(10)));
        redis.opsForValue().set(lockKey(5L), "other-server", Duration.ofSeconds(30));
        AtomicInteger computed = new AtomicInteger();

        Future<String> result = pool.submit(() -> server.cache.get(5L, () -> {
            computed.incrementAndGet();
            return "computed";
        }));
        server.awaitMisses(1);
        Thread.sleep(300);
        redis.opsForValue().set(cacheKey(5L), "\"stored-by-other-server\"", Duration.ofMinutes(5));
        redis.delete(lockKey(5L));

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("stored-by-other-server");
        assertThat(computed).hasValue(0);
        assertThat(server.count("uhsuh.cache.lock.attempts", "acquired")).isEqualTo(1.0);
        assertThat(redis.hasKey(lockKey(5L))).isFalse();
    }

    @Test
    void 락을_기다리는_요청은_다른_서버가_저장한_결과를_가져다_쓴다() throws Exception {
        Server server = server(LOCK);
        redis.opsForValue().set(lockKey(6L), "other-server", Duration.ofSeconds(30));
        AtomicInteger computed = new AtomicInteger();

        Future<String> result = pool.submit(() -> server.cache.get(6L, () -> {
            computed.incrementAndGet();
            return "computed";
        }));
        Thread.sleep(200);
        redis.opsForValue().set(cacheKey(6L), "\"stored-by-other-server\"", Duration.ofMinutes(5));

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("stored-by-other-server");
        assertThat(computed).hasValue(0);
        assertThat(redis.opsForValue().get(lockKey(6L))).isEqualTo("other-server");
    }

    @Test
    void 계산이_실패하면_락을_풀고_다음_요청이_다시_계산한다() {
        Server server = server(LOCK);
        IllegalStateException failure = new IllegalStateException("집계 실패");

        assertThatThrownBy(() -> server.cache.get(7L, () -> { throw failure; }))
                .isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCause(failure);
        assertThat(redis.hasKey(lockKey(7L))).isFalse();
        assertThat(redis.hasKey(cacheKey(7L))).isFalse();

        String value = server.cache.get(7L, () -> "graph-7");

        assertThat(value).isEqualTo("graph-7");
        assertThat(redis.opsForValue().get(cacheKey(7L))).isEqualTo("\"graph-7\"");
    }

    @Test
    void 락을_기다리다_시간이_지나면_직접_계산하고_비어_있을_때만_저장한다() throws Exception {
        Server server = server(new CacheLockSettings(Duration.ofSeconds(10), Duration.ofMillis(300), Duration.ofMillis(20), Duration.ofSeconds(10)));
        redis.opsForValue().set(lockKey(8L), "stuck-server", Duration.ofSeconds(30));
        AtomicInteger computed = new AtomicInteger();

        long started = System.nanoTime();
        String value = server.cache.get(8L, () -> {
            computed.incrementAndGet();
            return "graph-8";
        });
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(value).isEqualTo("graph-8");
        assertThat(computed).hasValue(1);
        assertThat(elapsed).isBetween(Duration.ofMillis(300), Duration.ofSeconds(3));
        assertThat(server.count("uhsuh.cache.lock.attempts", "timeout")).isEqualTo(1.0);
        assertThat(redis.opsForValue().get(cacheKey(8L))).isEqualTo("\"graph-8\"");
        assertThat(redis.opsForValue().get(lockKey(8L))).isEqualTo("stuck-server");
    }

    @Test
    void 락이_만료된_뒤_늦게_끝난_계산은_새_주인의_값을_덮지_않고_새_락도_지우지_않는다() throws Exception {
        Server slow = server(new CacheLockSettings(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofSeconds(10)));
        CountDownLatch computing = new CountDownLatch(1);

        Future<String> late = pool.submit(() -> slow.cache.get(9L, () -> {
            computing.countDown();
            Thread.sleep(900);
            return "old-graph";
        }));
        assertThat(computing.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(500);
        assertThat(redis.opsForValue().setIfAbsent(lockKey(9L), "new-owner", Duration.ofSeconds(30))).isTrue();
        redis.opsForValue().set(cacheKey(9L), "\"new-graph\"", Duration.ofMinutes(5));

        assertThat(late.get(10, TimeUnit.SECONDS)).isEqualTo("old-graph");
        assertThat(redis.opsForValue().get(cacheKey(9L))).isEqualTo("\"new-graph\"");
        assertThat(redis.opsForValue().get(lockKey(9L))).isEqualTo("new-owner");
        assertThat(slow.count("uhsuh.cache.puts", "rejected")).isEqualTo(1.0);
    }

    @Test
    void 락을_기다리던_스레드가_인터럽트되면_계산하지_않고_인터럽트_상태를_지킨다() throws Exception {
        Server server = server(LOCK);
        redis.opsForValue().set(lockKey(10L), "other-server", Duration.ofSeconds(30));
        AtomicInteger computed = new AtomicInteger();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            try {
                server.cache.get(10L, () -> {
                    computed.incrementAndGet();
                    return "graph-10";
                });
            } catch (Throwable e) {
                thrown.set(e);
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        Thread.sleep(200);
        waiter.interrupt();
        waiter.join(5_000);

        assertThat(thrown.get()).isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(interruptedAfter).isTrue();
        assertThat(computed).hasValue(0);
    }

    @Test
    void 저장할_때마다_TTL이_55분에서_60분_사이로_달라진다() {
        Server server = server(LOCK);
        Set<Long> ttls = new HashSet<>();

        for (long key = 100; key < 140; key++) {
            long id = key;
            server.cache.get(id, () -> "graph-" + id);
            Long ttlMillis = redis.getExpire(cacheKey(id), TimeUnit.MILLISECONDS);
            assertThat(ttlMillis).isBetween(Duration.ofMinutes(55).minusSeconds(5).toMillis(), Duration.ofMinutes(60).toMillis());
            ttls.add(ttlMillis / 1_000);
        }

        assertThat(ttls).hasSizeGreaterThan(10);
    }

    @Test
    void Redis에_닿지_않으면_락과_캐시_없이_계산하고_한동안_Redis를_건너뛴다() {
        LettuceConnectionFactory down = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));
        down.afterPropertiesSet();
        factories.add(down);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DistributedSingleFlightCache cache = new DistributedSingleFlightCache(NAME, CACHE_PREFIX, LOCK_PREFIX,
                DistributedSingleFlightCache.bytesTemplate(down), serializer(), LOCK, TTL, registry);
        AtomicInteger computed = new AtomicInteger();

        String first = cache.get(11L, () -> "graph-" + computed.incrementAndGet());
        String second = cache.get(11L, () -> "graph-" + computed.incrementAndGet());

        assertThat(first).isEqualTo("graph-1");
        assertThat(second).isEqualTo("graph-2");
        assertThat(registry.get("uhsuh.cache.lock.attempts").tag("result", "error").counter().count()).isZero();
        assertThat(registry.get("uhsuh.cache.lock.attempts").tag("result", "acquired").counter().count()).isZero();
        assertThat(registry.get("uhsuh.cache.loads").counter().count()).isEqualTo(2.0);
    }

    @Test
    void 락을_잃었지만_새_주인이_없으면_비어_있을_때만_저장한다() throws Exception {
        Server slow = server(new CacheLockSettings(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofSeconds(10)));

        String value = slow.cache.get(12L, () -> {
            Thread.sleep(700);
            return "slow-graph";
        });

        assertThat(value).isEqualTo("slow-graph");
        assertThat(redis.opsForValue().get(cacheKey(12L))).isEqualTo("\"slow-graph\"");
        assertThat(slow.count("uhsuh.cache.puts", "rejected")).isEqualTo(1.0);
        assertThat(slow.count("uhsuh.cache.puts", "stored")).isEqualTo(1.0);
    }

    @Test
    void 같은_서버_안에서_계산이_실패하면_기다리던_요청도_같은_원인으로_실패한다() throws Exception {
        Server server = server(LOCK);
        AtomicInteger computed = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("집계 실패");
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            Thread.sleep(300);
            throw failure;
        };

        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> { start.await(); return server.cache.get(13L, loader); }));
        }
        start.countDown();

        for (Future<String> result : results) {
            assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(Cache.ValueRetrievalException.class)
                    .hasRootCause(failure);
        }
        assertThat(computed).hasValue(1);
        assertThat(redis.hasKey(lockKey(13L))).isFalse();
    }

    @Test
    void 같은_서버의_선행_계산이_너무_오래_걸리면_기다리던_요청은_따로_계산하지_않고_실패한다() throws Exception {
        Server server = server(new CacheLockSettings(Duration.ofMillis(300), Duration.ofMillis(200), Duration.ofMillis(20), Duration.ofSeconds(10)));
        AtomicInteger computed = new AtomicInteger();
        CountDownLatch leaderComputing = new CountDownLatch(1);
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            leaderComputing.countDown();
            Thread.sleep(1_500);
            return "graph-14";
        };

        Future<String> leader = pool.submit(() -> server.cache.get(14L, loader));
        assertThat(leaderComputing.await(5, TimeUnit.SECONDS)).isTrue();
        long started = System.nanoTime();
        Future<String> waiter = pool.submit(() -> server.cache.get(14L, loader));

        assertThatThrownBy(() -> waiter.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(Cache.ValueRetrievalException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_400));
        assertThat(leader.get(10, TimeUnit.SECONDS)).isEqualTo("graph-14");
        assertThat(computed).hasValue(1);
    }

    @Test
    void 선행_요청이_인터럽트되면_같은_서버의_기다리던_요청은_다시_시도한다() throws Exception {
        Server server = server(LOCK);
        redis.opsForValue().set(lockKey(15L), "other-server", Duration.ofSeconds(30));
        AtomicInteger computed = new AtomicInteger();
        Callable<String> loader = () -> {
            computed.incrementAndGet();
            return "computed";
        };
        AtomicReference<Throwable> leaderError = new AtomicReference<>();

        Thread leader = new Thread(() -> {
            try {
                server.cache.get(15L, loader);
            } catch (Throwable e) {
                leaderError.set(e);
            }
        });
        leader.start();
        server.awaitMisses(1);
        Future<String> waiter = pool.submit(() -> server.cache.get(15L, loader));
        server.awaitMisses(2);
        Thread.sleep(100);
        leader.interrupt();
        leader.join(5_000);
        Thread.sleep(200);
        redis.opsForValue().set(cacheKey(15L), "\"stored-by-other-server\"", Duration.ofMinutes(5));
        redis.delete(lockKey(15L));

        assertThat(leaderError.get()).isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo("stored-by-other-server");
        assertThat(computed).hasValue(0);
    }

    private Server server(CacheLockSettings lockSettings) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DistributedSingleFlightCache cache = new DistributedSingleFlightCache(NAME, CACHE_PREFIX, LOCK_PREFIX,
                DistributedSingleFlightCache.bytesTemplate(connectionFactory()), serializer(), lockSettings, TTL, registry);
        return new Server(cache, registry);
    }

    private LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factories.add(factory);
        return factory;
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> serializer() {
        return (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(String.class);
    }

    private static String cacheKey(long id) {
        return CACHE_PREFIX + NAME + "::" + id;
    }

    private static String lockKey(long id) {
        return LOCK_PREFIX + NAME + "::" + id;
    }

    private record Server(DistributedSingleFlightCache cache, SimpleMeterRegistry registry) {

        double count(String meter, String result) {
            return registry.get(meter).tag("result", result).counter().count();
        }

        void awaitMisses(int expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (count("cache.gets", "miss") < expected && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(count("cache.gets", "miss")).isGreaterThanOrEqualTo(expected);
        }
    }
}
