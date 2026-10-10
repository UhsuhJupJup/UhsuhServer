package uhsuhjupjup.backend.config.llm;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class CappedRetrySleeper implements com.anthropic.core.Sleeper, com.openai.core.Sleeper {

    private final Duration maxWait;

    CappedRetrySleeper(Duration maxWait) {
        this.maxWait = maxWait;
    }

    @Override
    public void sleep(Duration wait) {
        if (isLongerThanCap(wait)) {
            throw waitTooLong(wait);
        }
        try {
            Thread.sleep(notNegative(wait));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmRetryAbortedException("재시도를 기다리다 중단됐습니다", e);
        }
    }

    @Override
    public CompletableFuture<Void> sleepAsync(Duration wait) {
        if (isLongerThanCap(wait)) {
            return CompletableFuture.failedFuture(waitTooLong(wait));
        }
        return new CompletableFuture<Void>()
                .completeOnTimeout(null, notNegative(wait).toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
    }

    private boolean isLongerThanCap(Duration wait) {
        return wait.compareTo(maxWait) > 0;
    }

    private LlmRetryAbortedException waitTooLong(Duration wait) {
        return new LlmRetryAbortedException("재시도 대기 " + wait.toMillis() + "ms가 상한 "
                + maxWait.toMillis() + "ms보다 길어 다시 시도하지 않습니다");
    }

    private static Duration notNegative(Duration wait) {
        return wait.isNegative() ? Duration.ZERO : wait;
    }
}
