package uhsuhjupjup.backend.common.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import uhsuhjupjup.backend.config.CacheConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTestServers implements AutoCloseable {

    static final String NAME = "graphTest";
    static final String CACHE_PREFIX = "test:cache:";
    static final String LOCK_PREFIX = "test:lock:";
    static final TtlJitter REMOTE_TTL = new TtlJitter(Duration.ofMinutes(55), Duration.ofMinutes(60));
    static final LocalCacheSettings LOCAL = new LocalCacheSettings(Duration.ofMinutes(5), 1_000);
    static final CacheLockSettings LOCK = new CacheLockSettings(
            Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofSeconds(10));

    private final String host;
    private final int port;
    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final List<RedisMessageListenerContainer> containers = new ArrayList<>();

    CacheTestServers(String host, int port) {
        this.host = host;
        this.port = port;
    }

    LettuceConnectionFactory connectionFactory() {
        return connectionFactory(host, port);
    }

    LettuceConnectionFactory connectionFactory(String targetHost, int targetPort) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(targetHost, targetPort),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
        factory.afterPropertiesSet();
        factories.add(factory);
        return factory;
    }

    StringRedisTemplate redis() {
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory());
        redis.afterPropertiesSet();
        return redis;
    }

    Server server() {
        return server(LOCK, LOCAL, connectionFactory(), serializer());
    }

    Server server(CacheLockSettings lock) {
        return server(lock, LOCAL, connectionFactory(), serializer());
    }

    Server server(CacheLockSettings lock, LocalCacheSettings local, LettuceConnectionFactory factory,
                  RedisSerializer<Object> serializer) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong nanos = new AtomicLong();
        RedisCacheLayer remote = new RedisCacheLayer(NAME, CACHE_PREFIX, LOCK_PREFIX,
                RedisCacheLayer.bytesTemplate(factory), serializer, lock, REMOTE_TTL, registry);
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        TieredCache cache = new TieredCache(NAME, new LocalCache(local, nanos::get), remote, lock,
                new CacheInvalidationPublisher(redis), Long::valueOf, registry);
        return new Server(cache, registry, nanos, factory);
    }

    void listen(Server server) throws InterruptedException {
        RedisMessageListenerContainer container = CacheConfig.invalidationListenerContainer(server.factory(), List.of(server.cache()));
        container.afterPropertiesSet();
        container.start();
        containers.add(container);
        awaitSubscribers(containers.size());
    }

    void awaitSubscribers(long expected) throws InterruptedException {
        StringRedisTemplate redis = redis();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && subscribers(redis) < expected) {
            Thread.sleep(20);
        }
        assertThat(subscribers(redis)).isGreaterThanOrEqualTo(expected);
    }

    @SuppressWarnings("unchecked")
    private long subscribers(StringRedisTemplate redis) {
        Long count = redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
            var commands = (io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            try {
                return commands.pubsubNumsub(CacheInvalidationPublisher.CHANNEL.getBytes()).get(2, TimeUnit.SECONDS)
                        .values().stream().mapToLong(Long::longValue).sum();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return count == null ? 0 : count;
    }

    @SuppressWarnings("unchecked")
    void killSubscriberConnections() {
        redis().execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
            var commands = (io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            try {
                return commands.clientKill(io.lettuce.core.KillArgs.Builder.typePubsub()).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    static RedisSerializer<Object> serializer() {
        return (RedisSerializer<Object>) (RedisSerializer<?>) new Jackson2JsonRedisSerializer<>(String.class);
    }

    static String cacheKey(long id) {
        return CACHE_PREFIX + NAME + "::" + id;
    }

    static String lockKey(long id) {
        return LOCK_PREFIX + NAME + "::" + id;
    }

    @Override
    public void close() {
        containers.forEach(container -> {
            try {
                container.stop();
                container.destroy();
            } catch (Exception ignored) {
            }
        });
        factories.forEach(LettuceConnectionFactory::destroy);
    }

    record Server(TieredCache cache, SimpleMeterRegistry registry, AtomicLong nanos, LettuceConnectionFactory factory) {

        double count(String meter, String result) {
            return registry.find(meter).tag("result", result).counters().stream().mapToDouble(c -> c.count()).sum();
        }

        double remoteLookups() {
            return count("cache.gets", "hit") + count("cache.gets", "miss");
        }

        double loads() {
            return count("uhsuh.cache.loads", "computed");
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        void awaitLocalMisses(int expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (count("uhsuh.cache.local.gets", "miss") < expected && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(count("uhsuh.cache.local.gets", "miss")).isGreaterThanOrEqualTo(expected);
        }

        void awaitLocalEmpty(long key) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (cache.localValue(key) != null && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(cache.localValue(key)).isNull();
        }
    }

    static Ticker ticker(AtomicLong nanos) {
        return nanos::get;
    }
}
