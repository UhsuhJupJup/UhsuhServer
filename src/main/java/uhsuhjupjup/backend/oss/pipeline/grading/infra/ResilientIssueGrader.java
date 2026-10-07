package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Primary
@Component
class ResilientIssueGrader implements IssueGrader {

    static final String CLAUDE_CIRCUIT = "issueGrader";
    static final String GPT_CIRCUIT = "issueGraderGpt";

    private final List<Stage> stages;

    ResilientIssueGrader(CircuitBreakerRegistry circuitBreakerRegistry,
                         ObjectProvider<ClaudeIssueGrader> claude,
                         ObjectProvider<GptIssueGrader> gpt) {
        this.stages = List.of(
                Stage.of("Claude", circuitBreakerRegistry.circuitBreaker(CLAUDE_CIRCUIT), claude.getIfAvailable()),
                Stage.of("GPT", circuitBreakerRegistry.circuitBreaker(GPT_CIRCUIT), gpt.getIfAvailable()));
    }

    @Override
    public IssueGradingResult grade(String title, String body, List<String> labels) {
        List<IssueGradingException> failures = new ArrayList<>();
        List<String> outcomes = new ArrayList<>();
        for (Stage stage : stages) {
            if (stage.isOff()) {
                outcomes.add(stage.name() + " 꺼짐");
                continue;
            }
            if (isInterrupted()) {
                outcomes.add(stage.name() + " 부르지 않음");
                continue;
            }
            try {
                return stage.grade(title, body, labels);
            } catch (IssueGradingException failure) {
                failures.add(failure);
                outcomes.add(stage.name() + " " + failure.getReason() + "(" + failure.getMessage() + ")");
            }
        }
        throw failureOf(failures, outcomes);
    }

    private static IssueGradingException failureOf(List<IssueGradingException> failures, List<String> outcomes) {
        boolean interrupted = isInterrupted();
        String message = (interrupted ? "이슈 판정이 중단됐습니다: " : "이슈를 판정하지 못했습니다: ")
                + String.join(", ", outcomes);
        if (failures.isEmpty()) {
            return new IssueGradingException(Reason.UNAVAILABLE, message);
        }
        IssueGradingException decisive = decisiveOf(failures);
        Reason reason = interrupted ? Reason.UNAVAILABLE : decisive.getReason();
        IssueGradingException failure = new IssueGradingException(reason, message, decisive);
        failures.stream().filter(other -> other != decisive).forEach(failure::addSuppressed);
        return failure;
    }

    private static IssueGradingException decisiveOf(List<IssueGradingException> failures) {
        return failures.stream()
                .filter(IssueSpecificGradingFailure::isIssueSpecific)
                .reduce((earlier, later) -> later)
                .orElse(failures.get(failures.size() - 1));
    }

    private static boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    private record Stage(String name, CircuitBreaker circuit, IssueGrader grader) {

        static Stage of(String name, CircuitBreaker circuit, IssueGrader grader) {
            circuit.getEventPublisher().onStateTransition(event -> log.warn(
                    "이슈 판정 서킷 상태 변경 circuit={} from={} to={}", event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
            return new Stage(name, circuit, grader);
        }

        boolean isOff() {
            return grader == null;
        }

        IssueGradingResult grade(String title, String body, List<String> labels) {
            try {
                return circuit.executeSupplier(() -> gradeOrFailSafely(title, body, labels));
            } catch (CallNotPermittedException e) {
                log.debug("{} 판정 서킷이 열려 있어 부르지 않음", name);
                throw new IssueGradingException(Reason.UNAVAILABLE, name + " 판정 서킷이 열려 있어 부르지 않았습니다");
            } catch (IssueGradingException failure) {
                log.warn("{} 이슈 판정 실패 reason={} {}", name, failure.getReason(), failure.getMessage());
                throw failure;
            }
        }

        private IssueGradingResult gradeOrFailSafely(String title, String body, List<String> labels) {
            try {
                return grader.grade(title, body, labels);
            } catch (RuntimeException e) {
                IssueGradingException failure = e instanceof IssueGradingException gradingFailure
                        ? gradingFailure
                        : new IssueGradingException(Reason.UNAVAILABLE,
                                name + " 판정기가 예상 밖 예외를 던졌습니다(" + SafeFailureText.classNamesOf(e) + ")");
                if (isInterrupted()) {
                    throw new GradingInterruptedException(name + " 판정 중에 스레드가 중단됐습니다", failure);
                }
                throw failure;
            }
        }
    }
}
