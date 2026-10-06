package uhsuhjupjup.backend.oss.pipeline.sync.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssIssueSyncAdminService {

    private static final String SUPPRESSED_SEPARATOR = ", ";

    private final OssIssueSyncService ossIssueSyncService;

    public OssIssueSyncResult syncRepo(Long repoId) {
        try {
            return ossIssueSyncService.syncRepo(repoId);
        } catch (GitHubClientException e) {
            warnIfSuppressed(repoId, e);
            throw new BusinessException(errorCodeFor(e.getReason()));
        }
    }

    private static void warnIfSuppressed(Long repoId, GitHubClientException failure) {
        Throwable[] suppressed = failure.getSuppressed();
        if (suppressed.length == 0) {
            return;
        }
        log.warn("오픈소스 레포 {} 이슈 수집 실패(이유 {})를 기록하다 함께 실패함: {}",
                repoId, failure.getReason(), describe(suppressed));
    }

    private static String describe(Throwable[] suppressed) {
        return Arrays.stream(suppressed)
                .map(throwable -> throwable.getClass().getSimpleName() + ": " + throwable.getMessage())
                .collect(Collectors.joining(SUPPRESSED_SEPARATOR));
    }

    private static ErrorCode errorCodeFor(Reason reason) {
        if (reason == Reason.NOT_CONFIGURED) {
            return ErrorCode.GITHUB_TOKEN_REQUIRED;
        }
        return ErrorCode.GITHUB_UNAVAILABLE;
    }
}
