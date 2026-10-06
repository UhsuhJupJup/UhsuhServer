package uhsuhjupjup.backend.oss.pipeline.sync.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OssIssueSyncAdminServiceTest {

    private static final Long REPO_ID = 7L;

    @Mock
    private OssIssueSyncService ossIssueSyncService;

    @InjectMocks
    private OssIssueSyncAdminService ossIssueSyncAdminService;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(OssIssueSyncAdminService.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logs);
    }

    @Test
    void syncRepo_returnsWhatTheSyncReturned() {
        OssIssueSyncResult synced = new OssIssueSyncResult(
                false, OssIssueSyncResult.IncompleteReason.PAGE_LIMIT, 3, Map.of(ExclusionReason.PULL_REQUEST, 1),
                2, 0, 0);
        given(ossIssueSyncService.syncRepo(REPO_ID)).willReturn(synced);

        assertThat(ossIssueSyncAdminService.syncRepo(REPO_ID)).isSameAs(synced);
    }

    @Test
    void syncRepo_tokenMissing_throwsTokenRequired() {
        given(ossIssueSyncService.syncRepo(REPO_ID)).willThrow(new GitHubCredentialsMissingException());

        assertThatThrownBy(() -> ossIssueSyncAdminService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_TOKEN_REQUIRED));
        assertThat(logs.list).isEmpty();
    }

    static Stream<GitHubClientException> otherGitHubFailures() {
        return Stream.of(
                new GitHubClientException(Reason.UNAUTHORIZED, 401, "GitHub 요청 실패(상태 401)", null),
                new GitHubClientException(Reason.RATE_LIMITED, 403, "GitHub 요청 실패(상태 403)", null),
                new GitHubClientException(Reason.RATE_LIMITED, 429, "GitHub 요청 실패(상태 429)", null),
                new GitHubClientException(Reason.REJECTED, 404, "GitHub 요청 실패(상태 404)", null),
                new GitHubClientException(Reason.REDIRECT_REFUSED, 302, "GitHub API 밖으로 가는 리다이렉트", null),
                new GitHubClientException(Reason.INVALID_RESPONSE, 200, "GitHub 이슈 목록 응답을 읽지 못했습니다", null),
                new GitHubClientException(Reason.UNAVAILABLE, "GitHub가 응답하지 않습니다(시도 3번)", null));
    }

    @ParameterizedTest
    @MethodSource("otherGitHubFailures")
    void syncRepo_otherGitHubFailure_throwsGitHubUnavailable(GitHubClientException failure) {
        given(ossIssueSyncService.syncRepo(REPO_ID)).willThrow(failure);

        assertThatThrownBy(() -> ossIssueSyncAdminService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_UNAVAILABLE));
        assertThat(logs.list).isEmpty();
    }

    @Test
    void syncRepo_recordingTheFailureAlsoFailed_throwsUnavailableAndWarnsOneLineWithWhatAlsoFailed() {
        GitHubClientException notFound = new GitHubClientException(
                Reason.REJECTED, 404, "GitHub 요청 실패(상태 404)", null);
        notFound.addSuppressed(new CannotAcquireLockException("Lock wait timeout exceeded"));
        given(ossIssueSyncService.syncRepo(REPO_ID)).willThrow(notFound);

        assertThatThrownBy(() -> ossIssueSyncAdminService.syncRepo(REPO_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GITHUB_UNAVAILABLE));

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("레포 7 ", "REJECTED", "CannotAcquireLockException: Lock wait timeout exceeded");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"OSS_REPO_NOT_FOUND", "OSS_ISSUE_SYNC_IN_PROGRESS"})
    void syncRepo_missingRepoOrSyncInProgress_passesThroughAsIs(ErrorCode errorCode) {
        BusinessException failure = new BusinessException(errorCode);
        given(ossIssueSyncService.syncRepo(REPO_ID)).willThrow(failure);

        assertThatThrownBy(() -> ossIssueSyncAdminService.syncRepo(REPO_ID)).isSameAs(failure);
    }
}
