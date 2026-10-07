package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;

import java.util.function.Predicate;

public final class IssueSpecificGradingFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return isIssueSpecific(failure);
    }

    static boolean isIssueSpecific(Throwable failure) {
        return failure instanceof IssueGradingException gradingFailure
                && switch (gradingFailure.getReason()) {
                    case REFUSED, TRUNCATED, INVALID_OUTPUT, INVALID_INPUT -> true;
                    case UNAVAILABLE, REJECTED -> false;
                };
    }
}
