package uhsuhjupjup.backend.oss.issue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueCursor;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDifficultyFilter;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssuePageResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueResult;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OssIssueService {

    private static final OssIssueLanguage DEFAULT_LANGUAGE = OssIssueLanguage.KO;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final OssIssueRepository ossIssueRepository;
    private final OssIssueGradeRepository ossIssueGradeRepository;
    private final OssRepoRepository ossRepoRepository;

    public OssIssueDetailResult getDetail(Long issueId, OssIssueLanguage language) {
        OssIssue issue = ossIssueRepository.findWithRepoByIdAndRepoStatus(issueId, OssRepoStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_ISSUE_NOT_FOUND));
        OssIssueGrade grade = ossIssueGradeRepository.findCurrentByIssueId(issueId)
                .filter(current -> !current.isExcluded())
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_ISSUE_NOT_FOUND));
        return OssIssueDetailResult.of(issue, grade, language == null ? DEFAULT_LANGUAGE : language);
    }

    public OssIssuePageResult getRepoIssues(Long repoId, OssIssueDifficultyFilter difficulty,
                                            OssIssueLanguage language, OssIssueCursor cursor, Integer size) {
        OssRepo repo = ossRepoRepository.findByIdAndStatus(repoId, OssRepoStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        OssIssueLanguage chosen = language == null ? DEFAULT_LANGUAGE : language;
        OssIssueDifficultyFilter filter = difficulty == null ? OssIssueDifficultyFilter.all() : difficulty;
        int pageSize = clampSize(size);
        List<OssIssueGrade> found = ossIssueGradeRepository.findCurrentNotExcludedByRepoId(repo.getId(),
                filter.difficulties(), cursor == null ? null : cursor.githubCreatedAt(),
                cursor == null ? null : cursor.id(), Limit.of(pageSize + 1));
        if (found.size() <= pageSize) {
            return new OssIssuePageResult(chosen, results(repo, found, chosen), null);
        }
        List<OssIssueGrade> page = found.subList(0, pageSize);
        return new OssIssuePageResult(chosen, results(repo, page, chosen),
                OssIssueCursor.after(page.getLast().getIssue()));
    }

    private static List<OssIssueResult> results(OssRepo repo, List<OssIssueGrade> grades, OssIssueLanguage language) {
        return grades.stream()
                .map(grade -> OssIssueResult.of(repo, grade, language))
                .toList();
    }

    private static int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
