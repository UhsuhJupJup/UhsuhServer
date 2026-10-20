package uhsuhjupjup.backend.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class CacheMetrics {

    private final String cache;
    private final MeterRegistry registry;

    public CacheMetrics(String cache, MeterRegistry registry) {
        this.cache = cache;
        this.registry = registry;
    }

    public Counter counter(String name, String... tags) {
        return Counter.builder(name).tag("cache", cache).tags(tags).register(registry);
    }

    public Timer timer(String name, String... tags) {
        return Timer.builder(name).tag("cache", cache).tags(tags).register(registry);
    }

    public MeterRegistry registry() {
        return registry;
    }

    public String cache() {
        return cache;
    }
}
