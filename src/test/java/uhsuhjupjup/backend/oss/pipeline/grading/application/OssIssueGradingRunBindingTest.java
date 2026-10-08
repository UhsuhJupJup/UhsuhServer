package uhsuhjupjup.backend.oss.pipeline.grading.application;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.oss.github.application.GitHubClient;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueGradeRepository;
import uhsuhjupjup.backend.oss.issue.infra.OssIssueRepository;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class OssIssueGradingRunBindingTest {

    private static final Long REPO_ID = 7L;
    private static final String INVALID_VALUE = "::invalid::";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final OssRepoRepository ossRepoRepository = mock(OssRepoRepository.class);
    private final OssIssueRepository ossIssueRepository = mock(OssIssueRepository.class);
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final OssRepo repo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repo, "id", REPO_ID);
        given(ossRepoRepository.findByIdAndStatus(REPO_ID, OssRepoStatus.ACTIVE)).willReturn(Optional.of(repo));
        given(lockProvider.lock(any())).willReturn(Optional.of(mock(SimpleLock.class)));
        given(ossIssueRepository.findGradingCandidates(any(), anyInt(), any()))
                .willReturn(List.of());
    }

    @Test
    void envValues_reachTheRunLimits() {
        Map<String, Object> env = Map.of(
                "OSS_GRADING_RUN_MAX_ISSUES", "2",
                "OSS_GRADING_RUN_MAX_DURATION", "PT1M",
                "OSS_GRADING_RUN_MAX_FAILURES", "5");

        runnerWithEnv(env).run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(OssIssueGradingService.class).gradeRepo(REPO_ID);
        });

        then(ossIssueRepository).should().findGradingCandidates(REPO_ID, 5, Limit.of(3));
        assertThat(lockAtMostFor()).isEqualTo(Duration.ofMinutes(6));
    }

    @Test
    void withoutEnv_runsWithTwentyIssuesTenMinutesAndThreeFailures() {
        runnerWithEnv(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(OssIssueGradingService.class).gradeRepo(REPO_ID);
        });

        then(ossIssueRepository).should().findGradingCandidates(REPO_ID, 3, Limit.of(21));
        assertThat(lockAtMostFor()).isEqualTo(Duration.ofMinutes(15));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OSS_GRADING_RUN_MAX_ISSUES",
            "OSS_GRADING_RUN_MAX_DURATION",
            "OSS_GRADING_RUN_MAX_FAILURES"
    })
    void eachEnvName_isReadByTheRun(String envName) {
        runnerWithEnv(Map.of(envName, INVALID_VALUE)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().hasStackTraceContaining(INVALID_VALUE);
        });
    }

    private Duration lockAtMostFor() {
        ArgumentCaptor<LockConfiguration> lockConfiguration = ArgumentCaptor.forClass(LockConfiguration.class);
        then(lockProvider).should().lock(lockConfiguration.capture());
        return lockConfiguration.getValue().getLockAtMostFor();
    }

    private ApplicationContextRunner runnerWithEnv(Map<String, Object> env) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(OssRepoRepository.class, () -> ossRepoRepository)
                .withBean(OssIssueRepository.class, () -> ossIssueRepository)
                .withBean(OssIssueGradeRepository.class, () -> mock(OssIssueGradeRepository.class))
                .withBean(OssIssueGradeSaver.class, () -> mock(OssIssueGradeSaver.class))
                .withBean(GitHubClient.class, () -> mock(GitHubClient.class))
                .withBean(IssueGrader.class, () -> mock(IssueGrader.class))
                .withBean(LockProvider.class, () -> lockProvider)
                .withBean(Clock.class, () -> CLOCK)
                .withConfiguration(UserConfigurations.of(OssIssueGradingService.class));
    }
}
