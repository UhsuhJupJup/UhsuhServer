package uhsuhjupjup.backend.oss.pipeline.grading.domain;

import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;

public record OssIssueVerdict(
        OssIssueDifficulty difficulty,
        OssIssueEvidence problem,
        OssIssueEvidence reproduction,
        OssIssueEvidence cause,
        OssIssueEvidence fixDirection,
        boolean relatedPr,
        OssIssueGradeExclusion exclusion,
        String reasonKo,
        String reasonEn,
        String summaryKo,
        String summaryEn) {

    @Override
    public String toString() {
        return "OssIssueVerdict[difficulty=" + difficulty
                + ", problem=" + problem
                + ", reproduction=" + reproduction
                + ", cause=" + cause
                + ", fixDirection=" + fixDirection
                + ", relatedPr=" + relatedPr
                + ", exclusion=" + exclusion
                + ", reasonKo=" + lengthOf(reasonKo)
                + ", reasonEn=" + lengthOf(reasonEn)
                + ", summaryKo=" + lengthOf(summaryKo)
                + ", summaryEn=" + lengthOf(summaryEn)
                + "]";
    }

    private static String lengthOf(String text) {
        return text == null ? "null" : "<" + text.codePointCount(0, text.length()) + "자>";
    }
}
