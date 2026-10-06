package uhsuhjupjup.backend.oss.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.regex.Pattern;

@Entity
@Table(name = "oss_issue_grade")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssIssueGrade extends BaseEntity {

    private static final Pattern LOWERCASE_SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private OssIssue issue;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    private OssIssueDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem", nullable = false, length = 20)
    private OssIssueEvidence problem;

    @Enumerated(EnumType.STRING)
    @Column(name = "reproduction", nullable = false, length = 20)
    private OssIssueEvidence reproduction;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause", nullable = false, length = 20)
    private OssIssueEvidence cause;

    @Enumerated(EnumType.STRING)
    @Column(name = "fix_direction", nullable = false, length = 20)
    private OssIssueEvidence fixDirection;

    @Column(name = "related_pr", nullable = false)
    private boolean relatedPr;

    @Enumerated(EnumType.STRING)
    @Column(name = "exclusion", length = 20)
    private OssIssueGradeExclusion exclusion;

    @Column(name = "reason_ko", nullable = false, length = 500)
    private String reasonKo;

    @Column(name = "reason_en", nullable = false, length = 500)
    private String reasonEn;

    @Column(name = "summary_ko", length = 1500)
    private String summaryKo;

    @Column(name = "summary_en", length = 1500)
    private String summaryEn;

    @Column(name = "criteria_version", nullable = false, length = 40)
    private String criteriaVersion;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    private OssIssueGrade(OssIssue issue, OssIssueDifficulty difficulty,
                          OssIssueEvidence problem, OssIssueEvidence reproduction,
                          OssIssueEvidence cause, OssIssueEvidence fixDirection, boolean relatedPr,
                          OssIssueGradeExclusion exclusion,
                          String reasonKo, String reasonEn, String summaryKo, String summaryEn,
                          String criteriaVersion, String model, String sourceHash) {
        this.issue = issue;
        this.difficulty = difficulty;
        this.problem = problem;
        this.reproduction = reproduction;
        this.cause = cause;
        this.fixDirection = fixDirection;
        this.relatedPr = relatedPr;
        this.exclusion = exclusion;
        this.reasonKo = reasonKo;
        this.reasonEn = reasonEn;
        this.summaryKo = summaryKo;
        this.summaryEn = summaryEn;
        this.criteriaVersion = criteriaVersion;
        this.model = model;
        this.sourceHash = sourceHash;
    }

    public static OssIssueGrade create(OssIssue issue, OssIssueDifficulty difficulty,
                                       OssIssueEvidence problem, OssIssueEvidence reproduction,
                                       OssIssueEvidence cause, OssIssueEvidence fixDirection, boolean relatedPr,
                                       OssIssueGradeExclusion exclusion,
                                       String reasonKo, String reasonEn, String summaryKo, String summaryEn,
                                       String criteriaVersion, String model, String sourceHash) {
        requireDifficultyAndSummaryUnlessExcluded(exclusion, difficulty, summaryKo, summaryEn);
        requireSummaryInBothLanguagesOrNeither(summaryKo, summaryEn);
        requireLowercaseSha256Hex(sourceHash);
        return new OssIssueGrade(issue, difficulty, problem, reproduction, cause, fixDirection, relatedPr,
                exclusion, reasonKo, reasonEn, summaryKo, summaryEn, criteriaVersion, model, sourceHash);
    }

    private static void requireDifficultyAndSummaryUnlessExcluded(OssIssueGradeExclusion exclusion,
                                                                  OssIssueDifficulty difficulty,
                                                                  String summaryKo, String summaryEn) {
        if (exclusion == null && (difficulty == null || summaryKo == null || summaryEn == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static void requireSummaryInBothLanguagesOrNeither(String summaryKo, String summaryEn) {
        if ((summaryKo == null) != (summaryEn == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static void requireLowercaseSha256Hex(String sourceHash) {
        if (sourceHash == null || !LOWERCASE_SHA256_HEX.matcher(sourceHash).matches()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
