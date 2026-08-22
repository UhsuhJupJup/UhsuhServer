package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.emailsubscription.application.VerificationTokens;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisVerificationTokens implements VerificationTokens {

    private static final String KEY_PREFIX = "email:verify:";

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationTokens(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(Long emailSubscriberId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, emailSubscriberId.toString(), ttl);
        return token;
    }

    @Override
    public Optional<Long> consume(String token) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        return value == null ? Optional.empty() : Optional.of(Long.valueOf(value));
    }
}
