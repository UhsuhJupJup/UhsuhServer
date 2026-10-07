package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;

class GradingInterruptedException extends IssueGradingException {

    GradingInterruptedException(String message, IssueGradingException cause) {
        super(Reason.UNAVAILABLE, message, cause);
    }
}
