package uhsuhjupjup.backend.oss.pipeline.grading.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;

import static org.assertj.core.api.Assertions.assertThat;

class OssIssueVerdictTest {

    @Test
    void toString_showsTheChoicesAndTextLengthsInsteadOfTheTexts() {
        OssIssueVerdict verdict = new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT,
                true, null,
                "이유다.", "Reason.", "요약 🐛.", "Summary.");

        assertThat(verdict.toString()).isEqualTo("OssIssueVerdict[difficulty=MEDIUM, problem=PRESENT, "
                + "reproduction=PARTIAL, cause=ABSENT, fixDirection=PRESENT, relatedPr=true, exclusion=null, "
                + "reasonKo=<4자>, reasonEn=<7자>, summaryKo=<5자>, summaryEn=<8자>]");
    }

    @Test
    void toString_ofAnExcludedVerdict_leavesItsTextsOut() {
        OssIssueVerdict verdict = new OssIssueVerdict(OssIssueDifficulty.HARD,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, OssIssueGradeExclusion.SPAM,
                "REASONKOMARKER", "REASONENMARKER", null, null);

        assertThat(verdict.toString())
                .contains("exclusion=SPAM", "summaryKo=null", "summaryEn=null")
                .doesNotContain("REASONKOMARKER", "REASONENMARKER");
    }
}
