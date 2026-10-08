package uhsuhjupjup.backend.oss.pipeline.grading.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueBodyHash;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OssIssueGradeSaverTest {

    private static final Long ISSUE_ID = 11L;
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String SOURCE_HASH = OssIssueBodyHash.of("Steps to reproduce");
    private static final OssIssueVerdict VERDICT = new OssIssueVerdict(OssIssueDifficulty.EASY,
            OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT,
            false, null, "모두 있다.", "Everything is given.", "설정이 무시된다.", "The setting is ignored.");

    @Mock
    private OssIssueRepository ossIssueRepository;

    @Mock
    private OssIssueGradeRepository ossIssueGradeRepository;

    private OssIssueGradeSaver ossIssueGradeSaver;
    private OssIssue candidate;

    @BeforeEach
    void setUp() {
        ossIssueGradeSaver = new OssIssueGradeSaver(ossIssueRepository, ossIssueGradeRepository);
        OssRepo repo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);
        candidate = OssIssue.create(repo, 5_000L, 7, "Retry interval is ignored", "Steps to reproduce",
                LocalDateTime.of(2026, 10, 7, 12, 0));
        ReflectionTestUtils.setField(candidate, "id", ISSUE_ID);
        given(ossIssueRepository.getReferenceById(ISSUE_ID)).willReturn(candidate);
    }

    @Test
    void save_candidateThatFailedBefore_clearsItsFailuresBeforeInsertingTheGrade() {
        ReflectionTestUtils.setField(candidate, "gradingFailures", 2);
        ReflectionTestUtils.setField(candidate, "gradingFailureSourceHash", SOURCE_HASH);

        ossIssueGradeSaver.save(candidate, VERDICT, MODEL, SOURCE_HASH);

        InOrder inOrder = inOrder(ossIssueRepository, ossIssueGradeRepository);
        inOrder.verify(ossIssueRepository).clearGradingFailures(ISSUE_ID);
        inOrder.verify(ossIssueGradeRepository).save(any(OssIssueGrade.class));
    }

    @Test
    void save_candidateWithoutFailures_onlyInsertsTheGrade() {
        ossIssueGradeSaver.save(candidate, VERDICT, MODEL, SOURCE_HASH);

        then(ossIssueRepository).should(never()).clearGradingFailures(anyLong());
        then(ossIssueGradeRepository).should().save(any(OssIssueGrade.class));
    }

    @Test
    void save_storesTheVerdictWithCriteriaVersionAnsweredModelAndSourceHash() {
        ossIssueGradeSaver.save(candidate, VERDICT, MODEL, SOURCE_HASH);

        ArgumentCaptor<OssIssueGrade> saved = ArgumentCaptor.forClass(OssIssueGrade.class);
        then(ossIssueGradeRepository).should().save(saved.capture());
        assertThat(saved.getValue())
                .returns(candidate, OssIssueGrade::getIssue)
                .returns(OssIssueDifficulty.EASY, OssIssueGrade::getDifficulty)
                .returns("Everything is given.", OssIssueGrade::getReasonEn)
                .returns("v1", OssIssueGrade::getCriteriaVersion)
                .returns(MODEL, OssIssueGrade::getModel)
                .returns(SOURCE_HASH, OssIssueGrade::getSourceHash);
    }
}
