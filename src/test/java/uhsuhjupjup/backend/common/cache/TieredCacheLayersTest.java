package uhsuhjupjup.backend.common.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import uhsuhjupjup.backend.common.cache.CacheTestServers.Server;
import uhsuhjupjup.backend.support.SharedTestContainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final List<String> messages = new CopyOnWriteArrayList<>();

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
    void 무효화_메시지를_놓쳐도_로컬_TTL이_지나면_Redis의_새_값을_읽는다() {
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

    @Test
    void 조회_도중_무효화가_끼어들면_예전_값을_로컬에_다시_넣지_않는다() throws Exception {
        BlockingSerializer blocking = new BlockingSerializer(CacheTestServers.serializer());
        Server reader = servers.server(LOCK, LOCAL, servers.connectionFactory(), blocking);
        Server writer = servers.server();
        servers.listen(reader);
        servers.listen(writer);
        redis.opsForValue().set(cacheKey(9L), "\"old-graph\"", Duration.ofMinutes(50));

        blocking.blockNextRead();
        Future<String> slowRead = pool.submit(() -> reader.cache().get(9L, () -> "computed"));
        assertThat(blocking.awaitReadStarted()).isTrue();
        assertThat(writer.cache().refresh(9L, () -> "new-graph")).isEqualTo(RefreshOutcome.STORED);
        awaitCount(reader, "uhsuh.cache.local.invalidations", "key", 1);
        blocking.release();

        assertThat(slowRead.get(10, TimeUnit.SECONDS)).isEqualTo("old-graph");
        assertThat(reader.cache().localValue(9L)).isNull();
        assertThat(reader.count("uhsuh.cache.local.puts", "skipped-stale")).isEqualTo(1.0);
        assertThat(reader.cache().get(9L, () -> "computed")).isEqualTo("new-graph");
        assertThat(reader.cache().localValue(9L)).isEqualTo("new-graph");
    }

    @Test
    void 일반_조회로_채울_때는_무효화_메시지를_보내지_않고_갱신이_저장된_뒤에만_해당_키를_보낸다() throws Exception {
        Server first = servers.server();
        Server second = servers.server();
        servers.listen(first);
        servers.listen(second);
        RedisMessageListenerContainer probe = probeContainer();

        first.cache().get(10L, () -> "graph-10");
        second.cache().get(10L, () -> "never");
        first.cache().get(11L, () -> "graph-11");
        redis.delete(cacheKey(11L));
        first.cache().evict(11L);
        first.cache().get(11L, () -> "graph-11-again");
        Thread.sleep(300);
        assertThat(messages).isEmpty();

        RefreshOutcome outcome = first.cache().refresh(10L, () -> "graph-10-new");

        assertThat(outcome).isEqualTo(RefreshOutcome.STORED);
        awaitMessages(1);
        assertThat(messages).containsExactly(CacheTestServers.NAME + "::10");
        first.awaitLocalEmpty(10L);
        second.awaitLocalEmpty(10L);
        assertThat(first.cache().localValue(11L)).isEqualTo("graph-11-again");
        assertThat(redis.opsForValue().get(cacheKey(10L))).isEqualTo("\"graph-10-new\"");
        assertThat(second.cache().get(10L, () -> "never")).isEqualTo("graph-10-new");
        assertThat(second.cache().localValue(10L)).isEqualTo("graph-10-new");
        probe.stop();
    }

    @Test
    void 갱신은_Redis에_값이_있어도_다시_계산해_덮어쓴다() {
        Server server = servers.server();
        server.cache().get(12L, () -> "old-graph");

        RefreshOutcome outcome = server.cache().refresh(12L, () -> "new-graph");

        assertThat(outcome).isEqualTo(RefreshOutcome.STORED);
        assertThat(redis.opsForValue().get(cacheKey(12L))).isEqualTo("\"new-graph\"");
        assertThat(redis.getExpire(cacheKey(12L), TimeUnit.SECONDS)).isBetween(3_290L, 3_600L);
    }

    @Test
    void 갱신_계산이_실패하면_Redis와_메시지를_건드리지_않는다() throws Exception {
        Server server = servers.server();
        servers.listen(server);
        RedisMessageListenerContainer probe = probeContainer();
        server.cache().get(13L, () -> "old-graph");

        RefreshOutcome outcome = server.cache().refresh(13L, () -> { throw new IllegalStateException("집계 실패"); });

        assertThat(outcome).isEqualTo(RefreshOutcome.FAILED);
        Thread.sleep(300);
        assertThat(messages).isEmpty();
        assertThat(redis.opsForValue().get(cacheKey(13L))).isEqualTo("\"old-graph\"");
        assertThat(server.cache().localValue(13L)).isEqualTo("old-graph");
        probe.stop();
    }

    @Test
    void Redis가_멈추면_갱신은_자기_로컬만_비운다() {
        Server server = servers.server(LOCK, LOCAL, servers.connectionFactory("127.0.0.1", 1), CacheTestServers.serializer());
        server.cache().get(14L, () -> "old-graph");

        RefreshOutcome outcome = server.cache().refresh(14L, () -> "new-graph");

        assertThat(outcome).isEqualTo(RefreshOutcome.REDIS_UNAVAILABLE);
        assertThat(server.cache().localValue(14L)).isNull();
    }

    @Test
    void 구독이_다시_연결되면_놓친_메시지에_대비해_로컬_캐시를_비운다() throws Exception {
        Server server = servers.server();
        servers.listen(server);
        server.cache().get(15L, () -> "graph-15");
        assertThat(server.cache().localValue(15L)).isEqualTo("graph-15");

        servers.killSubscriberConnections();

        server.awaitLocalEmpty(15L);
        servers.awaitSubscribers(1);
    }

    private RedisMessageListenerContainer probeContainer() throws InterruptedException {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(servers.connectionFactory());
        container.addMessageListener((message, pattern) -> messages.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        Thread.sleep(300);
        return container;
    }

    private void awaitMessages(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (messages.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private void awaitCount(Server server, String meter, String result, double expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.count(meter, result) < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(server.count(meter, result)).isGreaterThanOrEqualTo(expected);
    }

    private static final class BlockingSerializer implements RedisSerializer<Object> {

        private final RedisSerializer<Object> delegate;
        private volatile CountDownLatch gate;
        private final CountDownLatch readStarted = new CountDownLatch(1);

        private BlockingSerializer(RedisSerializer<Object> delegate) {
            this.delegate = delegate;
        }

        void blockNextRead() {
            gate = new CountDownLatch(1);
        }

        boolean awaitReadStarted() throws InterruptedException {
            return readStarted.await(5, TimeUnit.SECONDS);
        }

        void release() {
            gate.countDown();
        }

        @Override
        public byte[] serialize(Object value) {
            return delegate.serialize(value);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            CountDownLatch current = gate;
            if (current != null && readStarted.getCount() > 0) {
                readStarted.countDown();
                try {
                    current.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return delegate.deserialize(bytes);
        }
    }
}
