package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import lombok.extern.slf4j.Slf4j;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.InvalidIssueGradingOutputException.Violation;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Evidence;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Exclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
final class IssueGradingOutputValidator {

    private static final int MAX_REASON_CODE_POINTS = 500;
    private static final int MAX_SUMMARY_CODE_POINTS = 1_500;
    private static final Pattern URL_OR_EMAIL = Pattern.compile(
            "https?://|\\bwww\\.|[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}",
            Pattern.CASE_INSENSITIVE);

    private IssueGradingOutputValidator() {
    }

    static OssIssueVerdict validate(IssueGradingOutput output) {
        List<Violation> violations = new ArrayList<>();
        Evidence cause = required("evidenceCause", output.evidenceCause(), violations);
        Evidence fixDirection = required("evidenceFixDirection", output.evidenceFixDirection(), violations);
        Evidence problem = required("evidenceProblem", output.evidenceProblem(), violations);
        Boolean relatedPr = required("evidenceRelatedPr", output.evidenceRelatedPr(), violations);
        Evidence reproduction = required("evidenceReproduction", output.evidenceReproduction(), violations);
        Exclusion exclusion = required("exclusion", output.exclusion(), violations);
        Level level = required("level", output.level(), violations);
        String reasonEn = text("reasonEn", output.reasonEn(), MAX_REASON_CODE_POINTS, violations);
        String reasonKo = text("reasonKo", output.reasonKo(), MAX_REASON_CODE_POINTS, violations);
        String givenSummaryEn = unwrap(output.summaryEn());
        String givenSummaryKo = unwrap(output.summaryKo());
        if (isExcluded(exclusion) && isEmpty(givenSummaryEn) && isEmpty(givenSummaryKo)) {
            givenSummaryEn = null;
            givenSummaryKo = null;
        }
        String summaryEn = givenSummaryEn == null
                ? null : text("summaryEn", givenSummaryEn, MAX_SUMMARY_CODE_POINTS, violations);
        String summaryKo = givenSummaryKo == null
                ? null : text("summaryKo", givenSummaryKo, MAX_SUMMARY_CODE_POINTS, violations);
        checkSummaryPresence(exclusion, givenSummaryEn != null, givenSummaryKo != null, violations);
        if (!violations.isEmpty()) {
            throw new InvalidIssueGradingOutputException(violations);
        }
        OssIssueEvidence causeValue = evidence(cause);
        OssIssueEvidence fixDirectionValue = evidence(fixDirection);
        OssIssueEvidence problemValue = evidence(problem);
        OssIssueEvidence reproductionValue = evidence(reproduction);
        OssIssueDifficulty difficulty = backedByEvidence(difficulty(level),
                causeValue, fixDirectionValue, problemValue, reproductionValue);
        return new OssIssueVerdict(difficulty,
                problemValue, reproductionValue, causeValue, fixDirectionValue,
                relatedPr, exclusion(exclusion),
                reasonKo, reasonEn, summaryKo, summaryEn);
    }

    private static OssIssueDifficulty backedByEvidence(OssIssueDifficulty difficulty, OssIssueEvidence cause,
                                                       OssIssueEvidence fixDirection, OssIssueEvidence problem,
                                                       OssIssueEvidence reproduction) {
        if (difficulty != OssIssueDifficulty.EASY) {
            return difficulty;
        }
        List<String> notPresent = new ArrayList<>();
        addIfNotPresent(notPresent, "evidenceCause", cause);
        addIfNotPresent(notPresent, "evidenceFixDirection", fixDirection);
        addIfNotPresent(notPresent, "evidenceProblem", problem);
        addIfNotPresent(notPresent, "evidenceReproduction", reproduction);
        if (notPresent.isEmpty()) {
            return difficulty;
        }
        log.info("이슈 판정 난이도를 EASY에서 MEDIUM으로 낮춤, PRESENT가 아닌 근거: {}", String.join(", ", notPresent));
        return OssIssueDifficulty.MEDIUM;
    }

    private static void addIfNotPresent(List<String> notPresent, String field, OssIssueEvidence evidence) {
        if (evidence != OssIssueEvidence.PRESENT) {
            notPresent.add(field + "=" + evidence);
        }
    }

    private static <T> T required(String field, T value, List<Violation> violations) {
        if (value == null) {
            violations.add(Violation.missing(field));
        }
        return value;
    }

    private static String text(String field, String value, int maxCodePoints, List<Violation> violations) {
        if (value == null) {
            violations.add(Violation.missing(field));
            return null;
        }
        String stripped = stripBlank(value);
        if (stripped.isEmpty()) {
            violations.add(Violation.blank(field));
            return null;
        }
        int codePoints = stripped.codePointCount(0, stripped.length());
        if (codePoints > maxCodePoints) {
            violations.add(Violation.tooLong(field, codePoints, maxCodePoints));
            return null;
        }
        if (URL_OR_EMAIL.matcher(stripped).find()) {
            violations.add(Violation.urlOrEmail(field));
            return null;
        }
        return stripped;
    }

    private static String stripBlank(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBlank(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isBlank(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isBlank(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    private static boolean isEmpty(String value) {
        return value == null || stripBlank(value).isEmpty();
    }

    private static boolean isExcluded(Exclusion exclusion) {
        return exclusion != null && exclusion != Exclusion.NONE;
    }

    private static String unwrap(Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }

    private static void checkSummaryPresence(Exclusion exclusion, boolean summaryEnGiven, boolean summaryKoGiven,
                                             List<Violation> violations) {
        if (exclusion == Exclusion.NONE) {
            if (!summaryEnGiven) {
                violations.add(Violation.missing("summaryEn"));
            }
            if (!summaryKoGiven) {
                violations.add(Violation.missing("summaryKo"));
            }
            return;
        }
        if (summaryEnGiven != summaryKoGiven) {
            violations.add(Violation.unpaired(summaryEnGiven ? "summaryKo" : "summaryEn"));
        }
    }

    private static OssIssueDifficulty difficulty(Level level) {
        return switch (level) {
            case EASY -> OssIssueDifficulty.EASY;
            case MEDIUM -> OssIssueDifficulty.MEDIUM;
            case HARD -> OssIssueDifficulty.HARD;
        };
    }

    private static OssIssueEvidence evidence(Evidence evidence) {
        return switch (evidence) {
            case PRESENT -> OssIssueEvidence.PRESENT;
            case PARTIAL -> OssIssueEvidence.PARTIAL;
            case ABSENT -> OssIssueEvidence.ABSENT;
        };
    }

    private static OssIssueGradeExclusion exclusion(Exclusion exclusion) {
        return switch (exclusion) {
            case NONE -> null;
            case UNKNOWN_CAUSE -> OssIssueGradeExclusion.UNKNOWN_CAUSE;
            case QUESTION -> OssIssueGradeExclusion.QUESTION;
            case SPAM -> OssIssueGradeExclusion.SPAM;
            case DUPLICATE -> OssIssueGradeExclusion.DUPLICATE;
        };
    }
}
