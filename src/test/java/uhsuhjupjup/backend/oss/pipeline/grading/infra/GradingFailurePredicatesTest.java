package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import io.github.resilience4j.core.ClassUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;

import static org.assertj.core.api.Assertions.assertThat;

class GradingFailurePredicatesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "UNAVAILABLE, true, false",
            "REJECTED, true, false",
            "REFUSED, false, true",
            "TRUNCATED, false, true",
            "INVALID_OUTPUT, false, true",
            "INVALID_INPUT, false, true"})
    void gradingFailure_isRecordedWhenItHitsEveryCallAndIgnoredWhenItHitsOneIssue(Reason reason, boolean recorded,
                                                                                 boolean ignored) {
        IssueGradingException failure = new IssueGradingException(reason, "판정 실패");

        assertThat(new SystemicGradingFailure().test(failure)).isEqualTo(recorded);
        assertThat(new IssueSpecificGradingFailure().test(failure)).isEqualTo(ignored);
    }

    @Test
    void interruptedCall_isNeitherRecordedNorAnIssueFailure() {
        GradingInterruptedException failure = new GradingInterruptedException("판정 중에 스레드가 중단됐습니다",
                new IssueGradingException(Reason.UNAVAILABLE, "재시도를 기다리다 중단됐습니다"));

        assertThat(failure.getReason()).isEqualTo(Reason.UNAVAILABLE);
        assertThat(new SystemicGradingFailure().test(failure)).isFalse();
        assertThat(new IssueSpecificGradingFailure().test(failure)).isFalse();
    }

    @Test
    void otherFailures_areRecordedAndNeverIgnored() {
        IllegalStateException failure = new IllegalStateException("예상 밖 실패");

        assertThat(new SystemicGradingFailure().test(failure)).isTrue();
        assertThat(new IssueSpecificGradingFailure().test(failure)).isFalse();
    }

    @Test
    void bothPredicates_canBeCreatedTheWayResilience4jCreatesThem() {
        assertThat(ClassUtils.instantiatePredicateClass(SystemicGradingFailure.class))
                .isInstanceOf(SystemicGradingFailure.class);
        assertThat(ClassUtils.instantiatePredicateClass(IssueSpecificGradingFailure.class))
                .isInstanceOf(IssueSpecificGradingFailure.class);
    }
}
