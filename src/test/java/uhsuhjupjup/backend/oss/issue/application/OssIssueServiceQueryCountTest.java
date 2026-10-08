package uhsuhjupjup.backend.oss.issue.application;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssuePageResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
@Import(OssIssueService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class OssIssueServiceQueryCountTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 10, 5, 9, 12, 44);
    private static final int LISTED_ISSUE_COUNT = 6;

    @Autowired
    private OssIssueService ossIssueService;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private OssIssueRepository ossIssueRepository;

    @Autowired
    private OssIssueGradeRepository ossIssueGradeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    private Long issueId;

    @BeforeEach
    void setUp() {
        OssRepo repo = ossRepoRepository.save(OssRepo.create(1L, "acme/fastqueue", null, "Go", 12_000));
        OssIssue issue = ossIssueRepository.save(
                OssIssue.create(repo, 1L, 1284, "Retry interval is ignored", "Steps to reproduce", OPENED_AT));
        ossIssueGradeRepository.save(grade(issue, "이전 이유", "Earlier reason."));
        ossIssueGradeRepository.save(grade(issue, "이유", "Reason."));
        issueId = issue.getId();
        entityManager.flush();
        entityManager.clear();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void getDetail_readsIssueWithItsRepoAndThenTheCurrentGradeInTwoStatements() {
        OssIssueDetailResult result = ossIssueService.getDetail(issueId, OssIssueLanguage.EN);

        assertThat(result.repoFullName()).isEqualTo("acme/fastqueue");
        assertThat(result.reason()).isEqualTo("Reason.");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void getDetail_missingIssue_stopsAfterTheFirstStatement() {
        assertThatThrownBy(() -> ossIssueService.getDetail(issueId + 1, OssIssueLanguage.KO))
                .isInstanceOf(BusinessException.class);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void getRepoIssues_readsRepoAndThenThePageWithItsIssuesInTwoStatementsWhateverThePageSize() {
        Long repoId = saveRepoWithGradedIssues(LISTED_ISSUE_COUNT);

        OssIssuePageResult onePerPage = ossIssueService.getRepoIssues(repoId, null, OssIssueLanguage.EN, null, 1);
        long statementsForOne = statistics.getPrepareStatementCount();
        entityManager.clear();
        statistics.clear();

        OssIssuePageResult allInOnePage = ossIssueService.getRepoIssues(repoId, null, OssIssueLanguage.EN, null,
                LISTED_ISSUE_COUNT);

        assertThat(onePerPage.items()).hasSize(1);
        assertThat(onePerPage.nextCursor()).isNotNull();
        assertThat(allInOnePage.items()).hasSize(LISTED_ISSUE_COUNT)
                .allSatisfy(item -> {
                    assertThat(item.repoFullName()).isEqualTo("acme/listed");
                    assertThat(item.title()).startsWith("Listed issue ");
                    assertThat(item.summary()).isEqualTo("Summary.");
                });
        assertThat(allInOnePage.nextCursor()).isNull();
        assertThat(statementsForOne).isEqualTo(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void getRepoIssues_nextPage_alsoTakesTwoStatements() {
        Long repoId = saveRepoWithGradedIssues(LISTED_ISSUE_COUNT);
        OssIssuePageResult first = ossIssueService.getRepoIssues(repoId, null, null, null, 2);
        entityManager.clear();
        statistics.clear();

        OssIssuePageResult second = ossIssueService.getRepoIssues(repoId, null, null, first.nextCursor(), 2);

        assertThat(second.items()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void getRepoIssues_activeRepoWithNothingListed_stillTakesTwoStatements() {
        Long repoId = saveRepoWithGradedIssues(0);

        OssIssuePageResult result = ossIssueService.getRepoIssues(repoId, null, null, null, null);

        assertThat(result.items()).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void getRepoIssues_missingRepo_stopsAfterTheFirstStatement() {
        Long missingRepoId = ossRepoRepository.findAll().stream().mapToLong(OssRepo::getId).max().orElseThrow() + 1;
        statistics.clear();

        assertThatThrownBy(() -> ossIssueService.getRepoIssues(missingRepoId, null, null, null, null))
                .isInstanceOf(BusinessException.class);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private Long saveRepoWithGradedIssues(int count) {
        OssRepo repo = ossRepoRepository.save(OssRepo.create(2L, "acme/listed", null, "Go", 3_000));
        for (int number = 1; number <= count; number++) {
            OssIssue listed = ossIssueRepository.save(OssIssue.create(repo, 100L + number, number,
                    "Listed issue " + number, "Body " + number, OPENED_AT.plusMinutes(number)));
            ossIssueGradeRepository.save(grade(listed, "이전 이유", "Earlier reason."));
            ossIssueGradeRepository.save(grade(listed, "이유", "Reason."));
        }
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        return repo.getId();
    }

    private static OssIssueGrade grade(OssIssue issue, String reasonKo, String reasonEn) {
        return OssIssueGrade.create(issue, OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
                false, null, reasonKo, reasonEn, "요약", "Summary.", "v1", "claude-haiku-4-5", issue.getBodyHash());
    }
}
