package uhsuhjupjup.backend.oss.issue.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.EASY;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.HARD;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.MEDIUM;

@MySqlDataJpaTest
class OssIssueGradeRepositoryTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 28, 17, 23, 29);
    private static final LocalDateTime TEN_AM = LocalDateTime.of(2026, 10, 6, 10, 0);
    private static final LocalDateTime ELEVEN_AM = LocalDateTime.of(2026, 10, 6, 11, 0);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 6, 12, 0);
    private static final Set<OssIssueDifficulty> EVERY_DIFFICULTY = EnumSet.allOf(OssIssueDifficulty.class);
    private static final Limit ENOUGH = Limit.of(100);

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
    void savedGrade_keepsItsColumns() {
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

        OssIssueGrade loaded = ossIssueGradeRepository.findById(id).orElseThrow();

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
    void gradesOfSameIssue_accumulateAndTheCurrentIsTheLargestIdNotTheLatestCreatedAtOrVersion() {
        OssIssue issue = issue(1L);
        saveWithCreatedAt(grade(issue, "v1"), TEN_AM);
        saveWithCreatedAt(grade(issue, "v3"), NOON);
        Long lastSaved = saveWithCreatedAt(grade(issue, "v2"), ELEVEN_AM);
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findAll())
                .extracting(OssIssueGrade::getCriteriaVersion)
                .containsExactlyInAnyOrder("v1", "v3", "v2");
        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId()))
                .get().extracting(OssIssueGrade::getId).isEqualTo(lastSaved);
    }

    @Test
    void findCurrentByIssueId_loadsGradeWithoutLoadingIssue() {
        OssIssue issue = issue(1L);
        ossIssueGradeRepository.saveAndFlush(grade(issue, "v1"));
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findCurrentByIssueId(issue.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getIssue())).isFalse();
    }

    @Test
    void excludedGradeWithoutDifficultyAndSummary_isStored() {
        OssIssue issue = issue(1L);
        Long id = ossIssueGradeRepository.saveAndFlush(OssIssueGrade.create(issue, null,
                OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT, OssIssueEvidence.ABSENT,
                false, OssIssueGradeExclusion.SPAM,
                "광고 링크만 있다.", "It only contains promotional links.",
                null, null,
                "v1", "gpt-4o-mini", issue.getBodyHash())).getId();
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findById(id).orElseThrow();

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
        Long id = ossIssueGradeRepository.saveAndFlush(grade(issue, "v1", gradedBodyHash)).getId();
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findById(id).orElseThrow();

        assertThat(loaded.getSourceHash()).isEqualTo(gradedBodyHash);
        assertThat(ossIssueRepository.findById(issue.getId()).orElseThrow().getBodyHash())
                .isNotEqualTo(gradedBodyHash);
    }

    @Test
    void reasonAndSummaryAtTheirLengthLimits_areStoredWholeCountingCodePoints() {
        OssIssue issue = issue(1L);
        String reason = "가".repeat(499) + "🐛";
        String summary = "가".repeat(1_499) + "🐛";
        Long id = ossIssueGradeRepository.saveAndFlush(OssIssueGrade.create(issue, OssIssueDifficulty.EASY,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                false, null, reason, reason, summary, summary, "v1", "claude-haiku-4-5", issue.getBodyHash())).getId();
        entityManager.clear();

        OssIssueGrade loaded = ossIssueGradeRepository.findById(id).orElseThrow();

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

    @Test
    void findCurrentByIssueId_returnsLatestGradeOfTheCurrentBodyEvenIfAGradeOfAnotherBodyCameLater() {
        OssIssue issue = issue(1L);
        ossIssueGradeRepository.save(grade(issue, "v1"));
        Long latestOfCurrentBody = ossIssueGradeRepository.save(grade(issue, "v2")).getId();
        ossIssueGradeRepository.save(grade(issue, "v2", OssIssueBodyHash.of("a body graded meanwhile")));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId()))
                .get().extracting(OssIssueGrade::getId).isEqualTo(latestOfCurrentBody);
    }

    @Test
    void findCurrentByIssueId_bodyChangedAfterGrading_returnsNothingUntilTheNewBodyIsGraded() {
        OssIssue issue = issue(1L);
        ossIssueGradeRepository.save(grade(issue, "v1"));
        issue.refresh(repo, issue.getNumber(), issue.getTitle(), "Edited after grading.");
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId())).isEmpty();

        Long gradeOfNewBody = ossIssueGradeRepository.saveAndFlush(
                grade(issue, "v1", OssIssueBodyHash.of("Edited after grading."))).getId();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(issue.getId()))
                .get().extracting(OssIssueGrade::getId).isEqualTo(gradeOfNewBody);
    }

    @Test
    void findCurrentByIssueId_looksOnlyAtThatIssue() {
        OssIssue graded = issue(1L);
        OssIssue ungraded = issue(2L);
        ossIssueGradeRepository.save(grade(graded, "v1"));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findCurrentByIssueId(ungraded.getId())).isEmpty();
        assertThat(ossIssueGradeRepository.findCurrentByIssueId(graded.getId())).isPresent();
    }

    @Test
    void existsByIssueIdAndSourceHash_matchesOnlyThatIssueAndThatHash() {
        OssIssue graded = issue(1L);
        OssIssue other = issue(2L);
        String oldBodyHash = OssIssueBodyHash.of("old body");
        ossIssueGradeRepository.save(grade(graded, "v1", oldBodyHash));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.existsByIssueIdAndSourceHash(graded.getId(), oldBodyHash)).isTrue();
        assertThat(ossIssueGradeRepository.existsByIssueIdAndSourceHash(graded.getId(), graded.getBodyHash()))
                .isFalse();
        assertThat(ossIssueGradeRepository.existsByIssueIdAndSourceHash(other.getId(), oldBodyHash)).isFalse();
    }

    @Test
    void findCurrentNotExcludedByRepoId_picksTheSameGradeAsFindCurrentByIssueIdAndDropsExcludedOnes() {
        OssIssue sameBodyGradedThrice = issueAt(repo, 1L, OPENED_AT);
        save(gradeOf(sameBodyGradedThrice, EASY, null, sameBodyGradedThrice.getBodyHash()));
        save(gradeOf(sameBodyGradedThrice, MEDIUM, null, sameBodyGradedThrice.getBodyHash()));
        Long latestOfThree = save(gradeOf(sameBodyGradedThrice, HARD, null, sameBodyGradedThrice.getBodyHash()));
        OssIssue bodyChangedAfterGrading = issueAt(repo, 2L, OPENED_AT);
        save(gradeOf(bodyChangedAfterGrading, EASY, null, bodyChangedAfterGrading.getBodyHash()));
        bodyChangedAfterGrading.refresh(repo, 2, bodyChangedAfterGrading.getTitle(), "Edited after grading.");
        OssIssue oldBodyGradedLater = issueAt(repo, 3L, OPENED_AT);
        Long gradeOfCurrentBody = save(gradeOf(oldBodyGradedLater, MEDIUM, null, oldBodyGradedLater.getBodyHash()));
        save(gradeOf(oldBodyGradedLater, EASY, null, OssIssueBodyHash.of("a body graded meanwhile")));
        OssIssue excludedAfterVisible = issueAt(repo, 4L, OPENED_AT);
        save(gradeOf(excludedAfterVisible, EASY, null, excludedAfterVisible.getBodyHash()));
        save(gradeOf(excludedAfterVisible, EASY, OssIssueGradeExclusion.SPAM, excludedAfterVisible.getBodyHash()));
        OssIssue visibleAfterExcluded = issueAt(repo, 5L, OPENED_AT);
        save(gradeOf(visibleAfterExcluded, MEDIUM, OssIssueGradeExclusion.QUESTION,
                visibleAfterExcluded.getBodyHash()));
        Long visibleAgain = save(gradeOf(visibleAfterExcluded, HARD, null, visibleAfterExcluded.getBodyHash()));
        OssIssue ungraded = issueAt(repo, 6L, OPENED_AT);
        OssIssue excludedWithOlderVisibleBody = issueAt(repo, 7L, OPENED_AT);
        save(gradeOf(excludedWithOlderVisibleBody, HARD, OssIssueGradeExclusion.DUPLICATE,
                excludedWithOlderVisibleBody.getBodyHash()));
        save(gradeOf(excludedWithOlderVisibleBody, EASY, null, OssIssueBodyHash.of("an older body")));
        entityManager.flush();
        entityManager.clear();
        List<OssIssue> issues = List.of(sameBodyGradedThrice, bodyChangedAfterGrading, oldBodyGradedLater,
                excludedAfterVisible, visibleAfterExcluded, ungraded, excludedWithOlderVisibleBody);

        Map<Long, Long> listed = gradeIdByIssueId(
                ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(), EVERY_DIFFICULTY, null, null,
                        ENOUGH));

        assertThat(listed).isEqualTo(currentNotExcludedGradeIdByIssueId(issues));
        assertThat(listed).containsOnly(
                entry(sameBodyGradedThrice.getId(), latestOfThree),
                entry(oldBodyGradedLater.getId(), gradeOfCurrentBody),
                entry(visibleAfterExcluded.getId(), visibleAgain));
    }

    @Test
    void findCurrentNotExcludedByRepoId_fetchesTheIssueButNotItsRepo() {
        OssIssue issue = issueAt(repo, 1L, OPENED_AT);
        save(gradeOf(issue, EASY, null, issue.getBodyHash()));
        entityManager.flush();
        entityManager.clear();

        List<OssIssueGrade> listed = ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(),
                EVERY_DIFFICULTY, null, null, ENOUGH);

        assertThat(listed).hasSize(1);
        assertThat(Hibernate.isInitialized(listed.get(0).getIssue())).isTrue();
        assertThat(Hibernate.isInitialized(listed.get(0).getIssue().getRepo())).isFalse();
    }

    @Test
    void findCurrentNotExcludedByRepoId_looksOnlyAtIssuesOfThatRepo() {
        OssRepo otherRepo = ossRepoRepository.save(OssRepo.create(1L, "acme/fastqueue", null, "Go", 12_000));
        OssIssue mine = issueAt(repo, 1L, OPENED_AT);
        Long mineGrade = save(gradeOf(mine, EASY, null, mine.getBodyHash()));
        OssIssue theirs = issueAt(otherRepo, 2L, OPENED_AT.plusHours(1));
        Long theirGrade = save(gradeOf(theirs, EASY, null, theirs.getBodyHash()));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(), EVERY_DIFFICULTY,
                null, null, ENOUGH)).extracting(OssIssueGrade::getId).containsExactly(mineGrade);
        assertThat(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(otherRepo.getId(), EVERY_DIFFICULTY,
                null, null, ENOUGH)).extracting(OssIssueGrade::getId).containsExactly(theirGrade);
        assertThat(ossIssueGradeRepository.findCurrentNotExcludedByRepoId(otherRepo.getId() + 1,
                EVERY_DIFFICULTY, null, null, ENOUGH)).isEmpty();
    }

    @Test
    void findCurrentNotExcludedByRepoId_keepsOnlyTheGivenDifficultiesOfTheCurrentGrade() {
        OssIssue easy = issueAt(repo, 1L, OPENED_AT.plusHours(3));
        Long easyGrade = save(gradeOf(easy, EASY, null, easy.getBodyHash()));
        OssIssue medium = issueAt(repo, 2L, OPENED_AT.plusHours(2));
        Long mediumGrade = save(gradeOf(medium, MEDIUM, null, medium.getBodyHash()));
        OssIssue regradedFromEasyToHard = issueAt(repo, 3L, OPENED_AT.plusHours(1));
        save(gradeOf(regradedFromEasyToHard, EASY, null, regradedFromEasyToHard.getBodyHash()));
        Long hardGrade = save(gradeOf(regradedFromEasyToHard, HARD, null, regradedFromEasyToHard.getBodyHash()));
        entityManager.flush();
        entityManager.clear();

        assertThat(listedGradeIds(Set.of(EASY))).containsExactly(easyGrade);
        assertThat(listedGradeIds(Set.of(MEDIUM))).containsExactly(mediumGrade);
        assertThat(listedGradeIds(Set.of(HARD))).containsExactly(hardGrade);
        assertThat(listedGradeIds(Set.of(EASY, HARD))).containsExactly(easyGrade, hardGrade);
        assertThat(listedGradeIds(EVERY_DIFFICULTY)).containsExactly(easyGrade, mediumGrade, hardGrade);
    }

    @Test
    void findCurrentNotExcludedByRepoId_listsNewestGithubIssueFirstAndTheLargerIdFirstOnTies() {
        OssIssue oldest = issueAt(repo, 1L, OPENED_AT.minusDays(2));
        OssIssue tiedFirst = issueAt(repo, 2L, OPENED_AT);
        OssIssue newest = issueAt(repo, 3L, OPENED_AT.plusDays(1));
        OssIssue tiedSecond = issueAt(repo, 4L, OPENED_AT);
        OssIssue middle = issueAt(repo, 5L, OPENED_AT.minusDays(1));
        for (OssIssue issue : List.of(oldest, tiedFirst, newest, tiedSecond, middle)) {
            save(gradeOf(issue, MEDIUM, null, issue.getBodyHash()));
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(listedIssueIds(null, null, ENOUGH)).containsExactly(newest.getId(), tiedSecond.getId(),
                tiedFirst.getId(), middle.getId(), oldest.getId());
        assertThat(listedIssueIds(null, null, Limit.of(2))).containsExactly(newest.getId(), tiedSecond.getId());
    }

    @Test
    void findCurrentNotExcludedByRepoId_afterCursor_continuesAfterItEvenAmongIssuesOfTheSameTime() {
        OssIssue newer = issueAt(repo, 1L, OPENED_AT.plusHours(1));
        OssIssue tiedLowId = issueAt(repo, 2L, OPENED_AT);
        OssIssue tiedMiddleId = issueAt(repo, 3L, OPENED_AT);
        OssIssue tiedHighId = issueAt(repo, 4L, OPENED_AT);
        OssIssue older = issueAt(repo, 5L, OPENED_AT.minusSeconds(1));
        for (OssIssue issue : List.of(newer, tiedLowId, tiedMiddleId, tiedHighId, older)) {
            save(gradeOf(issue, EASY, null, issue.getBodyHash()));
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(listedIssueIds(newer.getGithubCreatedAt(), newer.getId(), ENOUGH))
                .containsExactly(tiedHighId.getId(), tiedMiddleId.getId(), tiedLowId.getId(), older.getId());
        assertThat(listedIssueIds(OPENED_AT, tiedMiddleId.getId(), ENOUGH))
                .containsExactly(tiedLowId.getId(), older.getId());
        assertThat(listedIssueIds(OPENED_AT, tiedLowId.getId(), ENOUGH)).containsExactly(older.getId());
        assertThat(listedIssueIds(older.getGithubCreatedAt(), older.getId(), ENOUGH)).isEmpty();
        assertThat(listedIssueIds(OPENED_AT, tiedHighId.getId(), Limit.of(1))).containsExactly(tiedMiddleId.getId());
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

    private OssIssue issueAt(OssRepo owner, long githubIssueId, LocalDateTime openedAt) {
        return ossIssueRepository.save(OssIssue.create(owner, githubIssueId, (int) githubIssueId,
                "Issue " + githubIssueId, "Body of issue " + githubIssueId, openedAt));
    }

    private static OssIssueGrade gradeOf(OssIssue issue, OssIssueDifficulty difficulty,
                                         OssIssueGradeExclusion exclusion, String sourceHash) {
        return OssIssueGrade.create(issue, difficulty,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT,
                false, exclusion, "이유", "Reason.", "요약", "Summary.", "v1", "claude-haiku-4-5", sourceHash);
    }

    private Long save(OssIssueGrade grade) {
        return ossIssueGradeRepository.save(grade).getId();
    }

    private Map<Long, Long> currentNotExcludedGradeIdByIssueId(List<OssIssue> issues) {
        Map<Long, Long> current = new HashMap<>();
        for (OssIssue issue : issues) {
            ossIssueGradeRepository.findCurrentByIssueId(issue.getId())
                    .filter(grade -> !grade.isExcluded())
                    .ifPresent(grade -> current.put(issue.getId(), grade.getId()));
        }
        return current;
    }

    private static Map<Long, Long> gradeIdByIssueId(List<OssIssueGrade> grades) {
        return grades.stream().collect(Collectors.toMap(grade -> grade.getIssue().getId(), OssIssueGrade::getId));
    }

    private List<Long> listedGradeIds(Set<OssIssueDifficulty> difficulties) {
        return ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(), difficulties, null, null, ENOUGH)
                .stream().map(OssIssueGrade::getId).toList();
    }

    private List<Long> listedIssueIds(LocalDateTime afterGithubCreatedAt, Long afterIssueId, Limit limit) {
        return ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(), EVERY_DIFFICULTY,
                        afterGithubCreatedAt, afterIssueId, limit)
                .stream().map(grade -> grade.getIssue().getId()).toList();
    }
}
