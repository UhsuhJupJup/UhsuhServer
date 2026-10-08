package uhsuhjupjup.backend.oss.pipeline.grading.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueGradingCriteria;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;

@Component
@RequiredArgsConstructor
public class OssIssueGradeSaver {

    private final OssIssueRepository ossIssueRepository;
    private final OssIssueGradeRepository ossIssueGradeRepository;

    @Transactional
    public void save(OssIssue candidate, OssIssueVerdict verdict, String model, String sourceHash) {
        if (candidate.hasGradingFailures()) {
            ossIssueRepository.clearGradingFailures(candidate.getId());
        }
        ossIssueGradeRepository.save(OssIssueGrade.create(ossIssueRepository.getReferenceById(candidate.getId()),
                verdict.difficulty(), verdict.problem(), verdict.reproduction(), verdict.cause(),
                verdict.fixDirection(), verdict.relatedPr(), verdict.exclusion(),
                verdict.reasonKo(), verdict.reasonEn(), verdict.summaryKo(), verdict.summaryEn(),
                OssIssueGradingCriteria.VERSION, model, sourceHash));
    }

    @Transactional
    public void recordFailure(Long issueId, String sourceHash) {
        ossIssueRepository.recordGradingFailure(issueId, sourceHash);
    }
}
