package uhsuhjupjup.backend.oss.issue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OssIssueService {

    private static final OssIssueLanguage DEFAULT_LANGUAGE = OssIssueLanguage.KO;

    private final OssIssueRepository ossIssueRepository;
    private final OssIssueGradeRepository ossIssueGradeRepository;

    public OssIssueDetailResult getDetail(Long issueId, OssIssueLanguage language) {
        OssIssue issue = ossIssueRepository.findWithRepoByIdAndRepoStatus(issueId, OssRepoStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_ISSUE_NOT_FOUND));
        OssIssueGrade grade = ossIssueGradeRepository.findCurrentByIssueId(issueId)
                .filter(current -> !current.isExcluded())
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_ISSUE_NOT_FOUND));
        return OssIssueDetailResult.of(issue, grade, language == null ? DEFAULT_LANGUAGE : language);
    }
}
