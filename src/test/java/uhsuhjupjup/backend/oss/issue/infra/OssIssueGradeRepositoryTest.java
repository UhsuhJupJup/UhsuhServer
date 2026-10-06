package uhsuhjupjup.backend.oss.issue.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MySqlDataJpaTest
class OssIssueGradeRepositoryTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 28, 17, 23, 29);
    private static final LocalDateTime TEN_AM = LocalDateTime.of(2026, 10, 6, 10, 0);
    private static final LocalDateTime ELEVEN_AM = LocalDateTime.of(2026, 10, 6, 11, 0);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Autowired
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OssRepo repo;

    @BeforeEach
    void setUp() {
        repo = ossRepoRepository.save(OssRepo.create(6296790L, "spring-projects/spring-boot", null, "Java", 80_000));
    }

    @Test
    void findFirstByIssueIdOrderByIdDesc_returnsSavedGradeWithItsColumns() {
        OssIssue issue = issue(1L);
        Long id = ossIssueGradeRepository.saveAndFlush(OssIssueGrade.create(issue, OssIssueDifficulty.HARD,
                OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL,
                true, null,
                "재현 절차와 원인 함수는 있지만 수정 방향은 일부만 있다.",
                "Steps and the faulty function are given, but the fix is only sketched.",
                "설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다. 로딩 순서를 바꾸고 회귀 테스트를 더하면 된다.",
                "Settings are read in an order that resets retry.interval to its default. "
                        + "Reordering the loading and adding a regression test fixes it.",
                "v1", "claude-haiku-4-5-20251001", issue.getBodyHash())).getId();
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getIssue().getId()).isEqualTo(issue.getId());
        assertThat(loaded.getDifficulty()).isEqualTo(OssIssueDifficulty.HARD);
        assertThat(loaded.getProblem()).isEqualTo(OssIssueEvidence.ABSENT);
        assertThat(loaded.getReproduction()).isEqualTo(OssIssueEvidence.PRESENT);
        assertThat(loaded.getCause()).isEqualTo(OssIssueEvidence.PRESENT);
        assertThat(loaded.getFixDirection()).isEqualTo(OssIssueEvidence.PARTIAL);
        assertThat(loaded.isRelatedPr()).isTrue();
        assertThat(loaded.getExclusion()).isNull();
        assertThat(loaded.getReasonKo()).isEqualTo("재현 절차와 원인 함수는 있지만 수정 방향은 일부만 있다.");
        assertThat(loaded.getReasonEn())
                .isEqualTo("Steps and the faulty function are given, but the fix is only sketched.");
        assertThat(loaded.getSummaryKo())
                .isEqualTo("설정을 읽는 순서 때문에 retry.interval이 기본값으로 덮어써진다. 로딩 순서를 바꾸고 회귀 테스트를 더하면 된다.");
        assertThat(loaded.getSummaryEn()).isEqualTo("Settings are read in an order that resets retry.interval to its "
                + "default. Reordering the loading and adding a regression test fixes it.");
        assertThat(loaded.getCriteriaVersion()).isEqualTo("v1");
        assertThat(loaded.getModel()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(loaded.getSourceHash()).isEqualTo(issue.getBodyHash());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void gradesOfSameIssue_accumulateAndLatestIsByIdNotCreatedAtOrVersion() {
        OssIssue issue = issue(1L);
        saveWithCreatedAt(grade(issue, "v1"), TEN_AM);
        saveWithCreatedAt(grade(issue, "v3"), NOON);
        Long lastSaved = saveWithCreatedAt(grade(issue, "v2"), ELEVEN_AM);
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findAll())
                .extracting(OssIssueGrade::getCriteriaVersion)
                .containsExactlyInAnyOrder("v1", "v3", "v2");
        assertThat(ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()))
                .get().extracting(OssIssueGrade::getId).isEqualTo(lastSaved);
    }

    @Test
    void findFirstByIssueIdOrderByIdDesc_looksOnlyAtThatIssue() {
        OssIssue graded = issue(1L);
        OssIssue gradedLater = issue(2L);
        OssIssue ungraded = issue(3L);
        Long gradedId = ossIssueGradeRepository.save(grade(graded, "v1")).getId();
        ossIssueGradeRepository.save(grade(gradedLater, "v1"));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(graded.getId()))
                .get().extracting(OssIssueGrade::getId).isEqualTo(gradedId);
        assertThat(ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(ungraded.getId())).isEmpty();
    }

    @Test
    void findLatestByIssueIdIn_returnsGradeWithLargestIdOfEachRequestedIssue() {
        OssIssue thriceGraded = issue(1L);
        OssIssue onceGraded = issue(2L);
        OssIssue notRequested = issue(3L);
        OssIssue ungraded = issue(4L);
        saveWithCreatedAt(grade(thriceGraded, "v1"), TEN_AM);
        Long onceGradedLatest = ossIssueGradeRepository.save(grade(onceGraded, "v1")).getId();
        saveWithCreatedAt(grade(thriceGraded, "v3"), NOON);
        ossIssueGradeRepository.save(grade(notRequested, "v1"));
        Long thriceGradedLatest = saveWithCreatedAt(grade(thriceGraded, "v2"), ELEVEN_AM);
        ossIssueGradeRepository.save(grade(notRequested, "v2"));
        entityManager.flush();
        entityManager.clear();

        List<OssIssueGrade> latest = ossIssueGradeRepository.findLatestByIssueIdIn(
                List.of(thriceGraded.getId(), onceGraded.getId(), ungraded.getId()));

        assertThat(latest).extracting(OssIssueGrade::getId)
                .containsExactlyInAnyOrder(thriceGradedLatest, onceGradedLatest);
    }

    @Test
    void findLatestByIssueIdIn_noIds_returnsEmpty() {
        ossIssueGradeRepository.saveAndFlush(grade(issue(1L), "v1"));

        assertThat(ossIssueGradeRepository.findLatestByIssueIdIn(List.of())).isEmpty();
    }

    @Test
    void findFirstByIssueIdOrderByIdDesc_loadsGradeWithoutLoadingIssue() {
        OssIssue issue = issue(1L);
        ossIssueGradeRepository.saveAndFlush(grade(issue, "v1"));
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getIssue())).isFalse();
    }

    @Test
    void excludedGradeWithoutDifficultyAndSummary_isStored() {
        OssIssue issue = issue(1L);
        ossIssueGradeRepository.saveAndFlush(OssIssueGrade.create(issue, null,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, OssIssueGradeExclusion.SPAM,
                "광고 링크만 있다.", "It only contains promotional links.",
                null, null,
                "v1", "gpt-4o-mini", issue.getBodyHash()));
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()).orElseThrow();

        assertThat(loaded.getExclusion()).isEqualTo(OssIssueGradeExclusion.SPAM);
        assertThat(loaded.getDifficulty()).isNull();
        assertThat(loaded.getSummaryKo()).isNull();
        assertThat(loaded.getSummaryEn()).isNull();
        assertThat(loaded.getReasonKo()).isEqualTo("광고 링크만 있다.");
        assertThat(loaded.getReasonEn()).isEqualTo("It only contains promotional links.");
        assertThat(loaded.isRelatedPr()).isFalse();
    }

    @Test
    void sourceHash_staysTheHashOfTheGradedBodyAfterTheIssueBodyChanges() {
        OssIssue issue = issue(1L);
        String gradedBodyHash = issue.getBodyHash();
        issue.refresh(repo, issue.getNumber(), issue.getTitle(), "Edited while the old body was being graded.");
        ossIssueGradeRepository.saveAndFlush(grade(issue, "v1", gradedBodyHash));
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()).orElseThrow();

        assertThat(loaded.getSourceHash()).isEqualTo(gradedBodyHash);
        assertThat(ossIssueRepository.findById(issue.getId()).orElseThrow().getBodyHash())
                .isNotEqualTo(gradedBodyHash);
    }

    @Test
    void reasonAndSummaryAtTheirLengthLimits_areStoredWholeCountingCodePoints() {
        OssIssue issue = issue(1L);
        String reason = "가".repeat(499) + "🐛";
        String summary = "가".repeat(1_499) + "🐛";
        ossIssueGradeRepository.saveAndFlush(OssIssueGrade.create(issue, OssIssueDifficulty.EASY,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                false, null, reason, reason, summary, summary, "v1", "claude-haiku-4-5", issue.getBodyHash()));
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findFirstByIssueIdOrderByIdDesc(issue.getId()).orElseThrow();

        assertThat(loaded.getReasonKo()).isEqualTo(reason);
        assertThat(loaded.getSummaryEn()).isEqualTo(summary);
        assertThat(reason.codePointCount(0, reason.length())).isEqualTo(500);
        assertThat(summary.codePointCount(0, summary.length())).isEqualTo(1_500);
    }

    @Test
    void deletingIssue_deletesItsGradesOnly() {
        OssIssue deleted = issue(1L);
        OssIssue kept = issue(2L);
        ossIssueGradeRepository.save(grade(deleted, "v1"));
        ossIssueGradeRepository.save(grade(deleted, "v2"));
        Long keptGrade = ossIssueGradeRepository.save(grade(kept, "v1")).getId();
        entityManager.flush();
        entityManager.clear();

        ossIssueRepository.deleteById(deleted.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findAll()).extracting(OssIssueGrade::getId).containsExactly(keptGrade);
    }

    private OssIssue issue(long githubIssueId) {
        return ossIssueRepository.save(OssIssue.create(
                repo, githubIssueId, (int) githubIssueId, "Retry interval is ignored", "abc", OPENED_AT));
    }

    private OssIssueGrade grade(OssIssue issue, String criteriaVersion) {
        return grade(issue, criteriaVersion, issue.getBodyHash());
    }

    private OssIssueGrade grade(OssIssue issue, String criteriaVersion, String sourceHash) {
        return OssIssueGrade.create(issue, OssIssueDifficulty.EASY,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                false, null,
                "이유", "Reason.", "요약", "Summary.",
                criteriaVersion, "claude-haiku-4-5", sourceHash);
    }

    private Long saveWithCreatedAt(OssIssueGrade grade, LocalDateTime createdAt) {
        Long id = ossIssueGradeRepository.saveAndFlush(grade).getId();
        assertThat(jdbcTemplate.update("update oss_issue_grade set created_at = ? where id = ?", createdAt, id))
                .isEqualTo(1);
        return id;
    }
}
