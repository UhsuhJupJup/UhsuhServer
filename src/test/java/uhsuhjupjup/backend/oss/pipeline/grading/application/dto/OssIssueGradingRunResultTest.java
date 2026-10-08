package uhsuhjupjup.backend.oss.pipeline.grading.application.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssIssueGradingRunResultTest {

    @Test
    void counts_haveEveryUngradableReasonAndEveryIssueSpecificFailureIncludingZeros() {
        OssIssueGradingRunResult result = new OssIssueGradingRunResult(StopReason.COMPLETED, 3, 1, 0, 0, 0, 0, 0,
                Map.of(OssIssueUngradableReason.CLOSED, 1), Map.of(Reason.REFUSED, 1));

        Map<OssIssueUngradableReason, Integer> expectedUngradable = Arrays.stream(OssIssueUngradableReason.values())
                .collect(Collectors.toMap(reason -> reason,
                        reason -> reason == OssIssueUngradableReason.CLOSED ? 1 : 0));
        assertThat(result.ungradable()).containsExactlyInAnyOrderEntriesOf(expectedUngradable);
        assertThat(result.failed()).containsOnlyKeys(Reason.REFUSED, Reason.TRUNCATED, Reason.INVALID_OUTPUT,
                Reason.INVALID_INPUT).containsEntry(Reason.REFUSED, 1).containsEntry(Reason.INVALID_INPUT, 0);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"UNAVAILABLE", "REJECTED"})
    void failed_refusesFailuresOfEveryCallBecauseTheyAreNeverCounted(Reason reason) {
        assertThatThrownBy(() -> new OssIssueGradingRunResult(StopReason.GRADER_UNAVAILABLE, 1, 0, 1, 0, 0, 0, 0,
                Map.of(), Map.of(reason, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counts_cannotBeChangedAfterwards() {
        OssIssueGradingRunResult result = new OssIssueGradingRunResult(StopReason.COMPLETED, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of());

        assertThatThrownBy(() -> result.ungradable().put(OssIssueUngradableReason.GONE, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.failed().put(Reason.REFUSED, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
