package uhsuhjupjup.backend.oss.issue.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssIssueRepositoryTest {

    private static final long GITHUB_ISSUE_ID_BEYOND_INT = 5_611_573_057L;
    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 28, 17, 23, 29);
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String OLD_BODY_HASH = OssIssueBodyHash.of("old body");
    private static final int MAX_FAILURES = 3;

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByGithubIssueId_returnsSavedIssueWithItsColumns() {
        OssRepo repo = ossRepoRepository.save(repo(4542716L, "NixOS/nixpkgs"));
        Long id = ossIssueRepository.saveAndFlush(OssIssue.create(
                repo, GITHUB_ISSUE_ID_BEYOND_INT, 567_775, "Retry interval is ignored", "abc", OPENED_AT)).getId();
        entityManager.clear();

        OssIssue loaded = ossIssueRepository.findByGithubIssueId(GITHUB_ISSUE_ID_BEYOND_INT).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getRepo().getId()).isEqualTo(repo.getId());
        assertThat(loaded.getGithubIssueId()).isEqualTo(GITHUB_ISSUE_ID_BEYOND_INT);
        assertThat(loaded.getNumber()).isEqualTo(567_775);
        assertThat(loaded.getTitle()).isEqualTo("Retry interval is ignored");
        assertThat(loaded.getBodyHash()).isEqualTo(ABC_SHA256);
        assertThat(loaded.getGithubCreatedAt()).isEqualTo(OPENED_AT);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByGithubIssueId_unknownId_returnsEmpty() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossIssueRepository.saveAndFlush(issue(repo, 1L, 1));
        entityManager.clear();

        assertThat(ossIssueRepository.findByGithubIssueId(2L)).isEmpty();
    }

    @Test
    void findByGithubIssueId_loadsIssueWithoutLoadingRepo() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossIssueRepository.saveAndFlush(issue(repo, 1L, 1));
        entityManager.clear();

        OssIssue loaded = ossIssueRepository.findByGithubIssueId(1L).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getRepo())).isFalse();
    }

    @Test
    void findWithRepoByIdAndRepoStatus_loadsIssueTogetherWithItsRepo() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = ossIssueRepository.saveAndFlush(issue(repo, 1L, 1)).getId();
        entityManager.clear();

        OssIssue loaded = ossIssueRepository.findWithRepoByIdAndRepoStatus(id, OssRepoStatus.ACTIVE).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getRepo())).isTrue();
        assertThat(loaded.getRepo().getFullName()).isEqualTo("spring-projects/spring-boot");
    }

    @Test
    void findWithRepoByIdAndRepoStatus_findsIssueOnlyWhenIdAndRepoStatusBothMatch() {
        OssRepo active = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo suspended = repo(2L, "octocat/suspended");
        ReflectionTestUtils.setField(suspended, "status", OssRepoStatus.SUSPENDED);
        ossRepoRepository.save(suspended);
        Long ofActive = ossIssueRepository.save(issue(active, 1L, 1)).getId();
        Long ofSuspended = ossIssueRepository.save(issue(suspended, 2L, 1)).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueRepository.findWithRepoByIdAndRepoStatus(ofActive, OssRepoStatus.ACTIVE))
                .map(OssIssue::getId).hasValue(ofActive);
        assertThat(ossIssueRepository.findWithRepoByIdAndRepoStatus(ofSuspended, OssRepoStatus.ACTIVE)).isEmpty();
        assertThat(ossIssueRepository.findWithRepoByIdAndRepoStatus(ofSuspended, OssRepoStatus.SUSPENDED))
                .map(OssIssue::getId).hasValue(ofSuspended);
        assertThat(ossIssueRepository.findWithRepoByIdAndRepoStatus(ofSuspended + 1, OssRepoStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void findAllByGithubIssueIdIn_returnsOnlyIssuesWithThoseIdsAcrossRepos() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossIssueRepository.save(issue(springBoot, 10L, 1));
        ossIssueRepository.save(issue(react, 20L, 1));
        ossIssueRepository.save(issue(react, 30L, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueRepository.findAllByGithubIssueIdIn(List.of(10L, 30L, 99L)))
                .extracting(OssIssue::getGithubIssueId)
                .containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void duplicateGithubIssueId_violatesUniqueConstraintAcrossRepos() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossIssueRepository.saveAndFlush(issue(springBoot, 1L, 10));

        assertThatThrownBy(() -> ossIssueRepository.saveAndFlush(issue(react, 1L, 20)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_issue_github_issue_id");
    }

    @Test
    void sameNumberInDifferentRepos_isStored() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossIssueRepository.saveAndFlush(issue(springBoot, 1L, 1));
        ossIssueRepository.saveAndFlush(issue(react, 2L, 1));
        entityManager.clear();

        assertThat(ossIssueRepository.findAll()).extracting(OssIssue::getNumber).containsExactly(1, 1);
    }

    @Test
    void titleOfKoreanAndEmojiOverGitHubLimit_isStoredCutTo256CodePoints() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = ossIssueRepository.saveAndFlush(
                OssIssue.create(repo, 1L, 1, "🐛버그".repeat(100), null, OPENED_AT)).getId();
        entityManager.clear();

        String stored = ossIssueRepository.findById(id).orElseThrow().getTitle();

        assertThat(stored).isEqualTo("🐛버그".repeat(85) + "🐛");
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(256);
    }

    @Test
    void deletingRepo_deletesItsIssuesOnly() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        Long springBootIssue = ossIssueRepository.save(issue(springBoot, 1L, 1)).getId();
        Long reactIssue = ossIssueRepository.save(issue(react, 2L, 1)).getId();
        entityManager.flush();
        entityManager.clear();

        ossRepoRepository.deleteById(springBoot.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossIssueRepository.findById(springBootIssue)).isEmpty();
        assertThat(ossIssueRepository.findById(reactIssue)).isPresent();
    }

    @Test
    void issueTable_hasNoColumnForRawBody() {
        List<String> bodyColumns = jdbcTemplate.queryForList("""
                select concat(column_name, ' ', column_type) from information_schema.columns
                where table_schema = database() and table_name = 'oss_issue' and column_name like '%body%'
                """, String.class);

        assertThat(bodyColumns).containsExactly("body_hash varchar(64)");
    }

    @Test
    void savedIssue_startsWithoutGradingFailures() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = ossIssueRepository.saveAndFlush(issue(repo, 1L, 1)).getId();
        entityManager.clear();

        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(0, null));
        assertThat(ossIssueRepository.findById(id).orElseThrow())
                .returns(0, OssIssue::getGradingFailures)
                .returns(null, OssIssue::getGradingFailureSourceHash);
    }

    @Test
    void findGradingCandidates_returnsIssuesOfThatRepoNewestFirstUpToLimitBreakingTiesByLargerId() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo otherRepo = ossRepoRepository.save(repo(2L, "facebook/react"));
        Long oldest = saveIssue(repo, 11L, OPENED_AT.minusDays(2));
        Long newest = saveIssue(repo, 12L, OPENED_AT);
        Long middle = saveIssue(repo, 13L, OPENED_AT.minusDays(1));
        Long newestSavedLater = saveIssue(repo, 14L, OPENED_AT);
        saveIssue(otherRepo, 21L, OPENED_AT.plusDays(1));
        entityManager.clear();

        assertThat(candidateIds(repo, 3)).containsExactly(newestSavedLater, newest, middle);
        assertThat(candidateIds(repo, 10)).containsExactly(newestSavedLater, newest, middle, oldest);
    }

    @Test
    void findGradingCandidates_skipsIssueGradedForItsCurrentBodyButKeepsIssueGradedOnlyForAnOldBody() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssIssue graded = ossIssueRepository.save(issue(repo, 11L, 1));
        OssIssue gradedForOldBody = ossIssueRepository.save(issue(repo, 12L, 2));
        Long ungraded = ossIssueRepository.save(issue(repo, 13L, 3)).getId();
        ossIssueGradeRepository.save(grade(graded, OLD_BODY_HASH));
        ossIssueGradeRepository.save(grade(graded, graded.getBodyHash()));
        ossIssueGradeRepository.save(grade(gradedForOldBody, OLD_BODY_HASH));
        entityManager.flush();
        entityManager.clear();

        assertThat(candidateIds(repo, 10)).containsExactlyInAnyOrder(gradedForOldBody.getId(), ungraded);
    }

    @Test
    void findGradingCandidates_skipsIssueThatFailedMaxTimesOnItsCurrentBodyOnly() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long failedMaxTimes = saveIssue(repo, 11L, OPENED_AT);
        Long failedFewerTimes = saveIssue(repo, 12L, OPENED_AT);
        Long failedMaxTimesOnOldBody = saveIssue(repo, 13L, OPENED_AT);
        entityManager.clear();
        givenGradingFailures(failedMaxTimes, MAX_FAILURES, ABC_SHA256);
        givenGradingFailures(failedFewerTimes, MAX_FAILURES - 1, ABC_SHA256);
        givenGradingFailures(failedMaxTimesOnOldBody, MAX_FAILURES + 2, OLD_BODY_HASH);

        assertThat(candidateIds(repo, 10)).containsExactlyInAnyOrder(failedFewerTimes, failedMaxTimesOnOldBody);
    }

    @Test
    void recordGradingFailure_countsUpForTheSameHashAndStartsAgainAtOneForAnotherHash() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = saveIssue(repo, 11L, OPENED_AT);
        entityManager.clear();

        ossIssueRepository.recordGradingFailure(id, ABC_SHA256);
        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(1, ABC_SHA256));

        ossIssueRepository.recordGradingFailure(id, ABC_SHA256);
        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(2, ABC_SHA256));

        ossIssueRepository.recordGradingFailure(id, OLD_BODY_HASH);
        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(1, OLD_BODY_HASH));
    }

    @Test
    void recordAndClearGradingFailures_leaveIssueDataAndUpdatedAtAsTheyWere() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = saveIssue(repo, 11L, OPENED_AT);
        entityManager.clear();
        jdbcTemplate.update("update oss_issue set updated_at = ? where id = ?", LONG_AGO, id);

        ossIssueRepository.recordGradingFailure(id, OLD_BODY_HASH);
        ossIssueRepository.clearGradingFailures(id);

        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(0, null));
        assertThat(jdbcTemplate.queryForObject("select updated_at from oss_issue where id = ?", LocalDateTime.class,
                id)).isEqualTo(LONG_AGO);
        assertThat(ossIssueRepository.findById(id).orElseThrow())
                .returns("Retry interval is ignored", OssIssue::getTitle)
                .returns(ABC_SHA256, OssIssue::getBodyHash);
    }

    @Test
    void savingIssueLoadedBeforeFailureWasRecorded_keepsTheRecordedFailure() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = saveIssue(repo, 11L, OPENED_AT);
        entityManager.clear();
        OssIssue loadedBeforeFailure = ossIssueRepository.findById(id).orElseThrow();

        ossIssueRepository.recordGradingFailure(id, ABC_SHA256);
        loadedBeforeFailure.refresh(repo, 1, "Retry interval is ignored on restart", "edited body");
        entityManager.flush();

        assertThat(gradingFailuresOf(id)).isEqualTo(new GradingFailures(1, ABC_SHA256));
        assertThat(jdbcTemplate.queryForObject("select title from oss_issue where id = ?", String.class, id))
                .isEqualTo("Retry interval is ignored on restart");
    }

    private OssIssue issue(OssRepo repo, long githubIssueId, int number) {
        return OssIssue.create(repo, githubIssueId, number, "Retry interval is ignored", "abc", OPENED_AT);
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, null, "Java", 1_000);
    }

    private Long saveIssue(OssRepo repo, long githubIssueId, LocalDateTime openedAt) {
        return ossIssueRepository.saveAndFlush(OssIssue.create(
                repo, githubIssueId, (int) githubIssueId, "Retry interval is ignored", "abc", openedAt)).getId();
    }

    private List<Long> candidateIds(OssRepo repo, int limit) {
        return ossIssueRepository.findGradingCandidates(repo.getId(), MAX_FAILURES, Limit.of(limit)).stream()
                .map(OssIssue::getId)
                .toList();
    }

    private static OssIssueGrade grade(OssIssue issue, String sourceHash) {
        return OssIssueGrade.create(issue, OssIssueDifficulty.EASY,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
                false, null, "이유", "Reason.", "요약", "Summary.", "v1", "claude-haiku-4-5", sourceHash);
    }

    private void givenGradingFailures(Long issueId, int failures, String sourceHash) {
        jdbcTemplate.update("update oss_issue set grading_failures = ?, grading_failure_source_hash = ? where id = ?",
                failures, sourceHash, issueId);
    }

    private GradingFailures gradingFailuresOf(Long issueId) {
        return jdbcTemplate.queryForObject(
                "select grading_failures, grading_failure_source_hash from oss_issue where id = ?",
                (row, rowNumber) -> new GradingFailures(
                        row.getInt("grading_failures"), row.getString("grading_failure_source_hash")),
                issueId);
    }

    private record GradingFailures(int count, String sourceHash) {
    }
}
