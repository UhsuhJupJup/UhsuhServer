package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import java.util.function.Predicate;

public final class SystemicGradingFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return !(failure instanceof GradingInterruptedException)
                && !IssueSpecificGradingFailure.isIssueSpecific(failure);
    }
}
