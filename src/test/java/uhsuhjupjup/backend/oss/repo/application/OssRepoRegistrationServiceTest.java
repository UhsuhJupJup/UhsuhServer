package uhsuhjupjup.backend.oss.repo.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException;
import uhsuhjupjup.backend.oss.github.application.GitHubClientException.Reason;
import uhsuhjupjup.backend.oss.github.application.GitHubCredentialsMissingException;
import uhsuhjupjup.backend.oss.github.application.dto.GitHubRepo;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OssRepoRegistrationServiceTest {

    private static final long GITHUB_ID = 1296269L;
    private static final String CANONICAL_NAME = "octocat/Hello-World";
    private static final String CANONICAL_KEY = "octocat/hello-world";
    private static final GitHubRepo HELLO_WORLD = new GitHubRepo(
            GITHUB_ID, CANONICAL_NAME, "My first repository on GitHub!", "Java", 80, false, true, false);

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private OssRepoRepository ossRepoRepository;

    @Mock
    private OssRepoSaver ossRepoSaver;

    @InjectMocks
    private OssRepoRegistrationService ossRepoRegistrationService;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(OssRepoRegistrationService.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logs);
    }

    @Test
    void register_eligibleRepo_savesGitHubCanonicalNameAndReturnsSavedRepo() {
        given(gitHubClient.findRepo("OCTOCAT", "hello-world")).willReturn(Optional.of(HELLO_WORLD));
        given(ossRepoSaver.save(any(OssRepo.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        OssRepoResult result = ossRepoRegistrationService.register("OCTOCAT/hello-world");

        assertThat(result).isEqualTo(new OssRepoResult(
                10L, GITHUB_ID, CANONICAL_NAME, "My first repository on GitHub!", "Java", 80, OssRepoStatus.ACTIVE,
                List.of()));
        ArgumentCaptor<OssRepo> saved = ArgumentCaptor.forClass(OssRepo.class);
        then(ossRepoSaver).should().save(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo(CANONICAL_NAME);
        assertThat(saved.getValue().getFullNameKey()).isEqualTo(CANONICAL_KEY);
        then(ossRepoRepository).should().findByGithubId(GITHUB_ID);
        then(ossRepoRepository).should().findByFullNameKey(CANONICAL_KEY);
        assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] private={0}, hasIssues={1}, archived={2} -> {3}")
    @CsvSource({
            "true,  true,  false, OSS_REPO_PRIVATE",
            "true,  false, true,  OSS_REPO_PRIVATE",
            "false, false, false, OSS_REPO_ISSUES_DISABLED",
            "false, false, true,  OSS_REPO_ISSUES_DISABLED",
            "false, true,  true,  OSS_REPO_ARCHIVED"
    })
    void register_repoThatCannotTakeContributions_isRejectedWithFirstReasonInOrder(
            boolean isPrivate, boolean hasIssues, boolean archived, ErrorCode expected) {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(new GitHubRepo(
                GITHUB_ID, CANONICAL_NAME, null, null, 80, isPrivate, hasIssues, archived)));

        assertFailsWith(expected, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossRepoSaver).shouldHaveNoInteractions();
    }

    @Test
    void register_repoMissingOnGitHub_throwsNotFoundWithoutTouchingStore() {
        given(gitHubClient.findRepo("octocat", "nothing-here")).willReturn(Optional.empty());

        assertFailsWith(ErrorCode.GITHUB_REPO_NOT_FOUND, () -> ossRepoRegistrationService.register("octocat/nothing-here"));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossRepoSaver).shouldHaveNoInteractions();
    }

    @Test
    void register_sameGithubIdAlreadyStored_throwsAlreadyExistsWithoutSaving() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        given(ossRepoRepository.findByGithubId(GITHUB_ID))
                .willReturn(Optional.of(OssRepo.create(GITHUB_ID, "octocat/Hello-World-Before-Rename", null, null, 0)));

        assertFailsWith(ErrorCode.OSS_REPO_ALREADY_EXISTS, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoSaver).shouldHaveNoInteractions();
    }

    @Test
    void register_nameDifferingOnlyInCaseAlreadyStored_throwsAlreadyExistsWithoutSaving() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        given(ossRepoRepository.findByFullNameKey(CANONICAL_KEY))
                .willReturn(Optional.of(OssRepo.create(42L, "OctoCat/HELLO-WORLD", null, null, 0)));

        assertFailsWith(ErrorCode.OSS_REPO_ALREADY_EXISTS, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoRepository).should().findByGithubId(GITHUB_ID);
        then(ossRepoSaver).shouldHaveNoInteractions();
    }

    @Test
    void register_losingRaceOnUniqueKey_throwsAlreadyExists() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        given(ossRepoRepository.findByGithubId(GITHUB_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(OssRepo.create(GITHUB_ID, CANONICAL_NAME, null, null, 80)));
        given(ossRepoSaver.save(any(OssRepo.class))).willThrow(
                new DataIntegrityViolationException("Duplicate entry '1296269' for key 'oss_repo.uk_oss_repo_github_id'"));

        assertFailsWith(ErrorCode.OSS_REPO_ALREADY_EXISTS, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoSaver).should().save(any(OssRepo.class));
    }

    @Test
    void register_integrityViolationWithoutConflictingRepo_propagatesOriginalException() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willReturn(Optional.of(HELLO_WORLD));
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("Data too long for column 'primary_language'");
        given(ossRepoSaver.save(any(OssRepo.class))).willThrow(violation);

        assertThatThrownBy(() -> ossRepoRegistrationService.register("octocat/Hello-World")).isSameAs(violation);
    }

    @Test
    void register_withoutGitHubToken_throwsTokenRequiredAndLogsReason() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willThrow(new GitHubCredentialsMissingException());

        assertFailsWith(ErrorCode.GITHUB_TOKEN_REQUIRED, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossRepoSaver).shouldHaveNoInteractions();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("octocat/Hello-World", "NOT_CONFIGURED", "상태 없음");
        });
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @CsvSource({
            "UNAUTHORIZED,     401",
            "RATE_LIMITED,     403",
            "RATE_LIMITED,     429",
            "REJECTED,         403",
            "REJECTED,         451",
            "REDIRECT_REFUSED, 301",
            "INVALID_RESPONSE, 200"
    })
    void register_gitHubRefusingOrAnsweringBadly_throwsUnavailableAndLogsReasonAndStatus(Reason reason, int status) {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willThrow(new GitHubClientException(
                reason, status, "GitHub 요청 실패(상태 " + status + "): /repos/octocat/Hello-World", null));

        assertFailsWith(ErrorCode.GITHUB_UNAVAILABLE, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossRepoSaver).shouldHaveNoInteractions();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(reason.name(), "상태 " + status);
        });
    }

    @Test
    void register_gitHubNotResponding_throwsUnavailableAndLogsReasonWithoutStatus() {
        given(gitHubClient.findRepo("octocat", "Hello-World")).willThrow(new GitHubClientException(Reason.UNAVAILABLE,
                "GitHub가 응답하지 않습니다(시도 3번, 마지막 HttpTimeoutException): /repos/octocat/Hello-World", null));

        assertFailsWith(ErrorCode.GITHUB_UNAVAILABLE, () -> ossRepoRegistrationService.register("octocat/Hello-World"));

        then(ossRepoRepository).shouldHaveNoInteractions();
        then(ossRepoSaver).shouldHaveNoInteractions();
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("UNAVAILABLE", "상태 없음", "HttpTimeoutException");
        });
    }

    private static void assertFailsWith(ErrorCode expected, ThrowingCallable register) {
        assertThatThrownBy(register)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    private static OssRepo withId(OssRepo repo, long id) {
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }
}
