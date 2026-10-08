package uhsuhjupjup.backend.oss.pipeline.grading.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import uhsuhjupjup.backend.common.auth.AdminMemberArgumentResolver;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseAuthInterceptor;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;
import uhsuhjupjup.backend.oss.pipeline.grading.application.OssIssueGradingService;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult;
import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.OssIssueGradingRunResult.StopReason;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueUngradableReason;
import uhsuhjupjup.backend.support.AdminMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOssIssueGradingControllerTest {

    private static final String GRADE_URL = "/api/admin/oss/repos/{repoId}/issues/grade";
    private static final int DEFAULT_LIMIT = 5;
    private static final OssIssueGradingRunResult NOTHING_LEFT =
            new OssIssueGradingRunResult(StopReason.COMPLETED, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of());

    @Mock
    private OssIssueGradingService ossIssueGradingService;

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcResolvingAdminWith(
                new AdminMemberStubResolver(MemberFixture.member(1L, "admin@example.com")));
    }

    @Test
    void gradeIssues_returns200WithTheRunSummaryAndEveryReasonKey() throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, DEFAULT_LIMIT)).willReturn(new OssIssueGradingRunResult(
                StopReason.ISSUE_LIMIT, 5, 2, 0, 0, 1, 0, 0,
                Map.of(OssIssueUngradableReason.CLOSED, 1), Map.of(Reason.INVALID_OUTPUT, 1)));

        mockMvc.perform(post(GRADE_URL, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.stopReason").value("ISSUE_LIMIT"))
                .andExpect(jsonPath("$.selected").value(5))
                .andExpect(jsonPath("$.graded").value(2))
                .andExpect(jsonPath("$.held").value(0))
                .andExpect(jsonPath("$.notStarted").value(0))
                .andExpect(jsonPath("$.sameBodySkipped").value(1))
                .andExpect(jsonPath("$.failureLimitSkipped").value(0))
                .andExpect(jsonPath("$.githubSkipped").value(0))
                .andExpect(content().string(containsString("\"ungradable\":{\"GONE\":0,\"TRANSFERRED\":0,"
                        + "\"BLOCKED\":0,\"ID_MISMATCH\":0,\"CLOSED\":1,\"PULL_REQUEST\":0,\"BOT_AUTHOR\":0,"
                        + "\"ASSIGNED\":0,\"DELETED\":0}")))
                .andExpect(content().string(containsString(
                        "\"failed\":{\"REFUSED\":0,\"TRUNCATED\":0,\"INVALID_OUTPUT\":1,\"INVALID_INPUT\":0}")));
    }

    @Test
    void gradeIssues_stoppedMidway_returns200WithTheReasonAndWhatWasGradedBeforeIt() throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, DEFAULT_LIMIT)).willReturn(new OssIssueGradingRunResult(
                StopReason.GRADER_UNAVAILABLE, 5, 2, 1, 2, 0, 0, 0, Map.of(), Map.of()));

        mockMvc.perform(post(GRADE_URL, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopReason").value("GRADER_UNAVAILABLE"))
                .andExpect(jsonPath("$.graded").value(2))
                .andExpect(jsonPath("$.held").value(1))
                .andExpect(jsonPath("$.notStarted").value(2));
    }

    @Test
    void gradeIssues_withoutLimit_gradesUpToFive() throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, DEFAULT_LIMIT)).willReturn(NOTHING_LEFT);

        mockMvc.perform(post(GRADE_URL, 10))
                .andExpect(status().isOk());

        then(ossIssueGradingService).should().gradeRepo(10L, DEFAULT_LIMIT);
    }

    @Test
    void gradeIssues_emptyLimit_gradesUpToFive() throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, DEFAULT_LIMIT)).willReturn(NOTHING_LEFT);

        mockMvc.perform(post(GRADE_URL, 10).param("limit", ""))
                .andExpect(status().isOk());

        then(ossIssueGradingService).should().gradeRepo(10L, DEFAULT_LIMIT);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20})
    void gradeIssues_limitAtTheBounds_isPassedAsGiven(int limit) throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, limit)).willReturn(NOTHING_LEFT);

        mockMvc.perform(post(GRADE_URL, 10).param("limit", String.valueOf(limit)))
                .andExpect(status().isOk());

        then(ossIssueGradingService).should().gradeRepo(10L, limit);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1 이상이어야 합니다.",
            "-1, 1 이상이어야 합니다.",
            "21, 20 이하여야 합니다."
    })
    void gradeIssues_limitOutOfRange_returns400NamingLimitWithoutGrading(String limit, String reason)
            throws Exception {
        mockMvc.perform(post(GRADE_URL, 10).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/admin/oss/repos/10/issues/grade"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(reason));

        then(ossIssueGradingService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "5.5", "3000000000"})
    void gradeIssues_limitThatIsNotAnInt_returns400NamingLimitWithoutGrading(String limit) throws Exception {
        mockMvc.perform(post(GRADE_URL, 10).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossIssueGradingService).shouldHaveNoInteractions();
    }

    @Test
    void gradeIssues_nonNumericRepoId_returns400WithoutGrading() throws Exception {
        mockMvc.perform(post(GRADE_URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("repoId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossIssueGradingService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "0", "abc"})
    void gradeIssues_memberWhoIsNotAdmin_returns403BeforeLookingAtTheLimit(String limit) throws Exception {
        AuthUser authUser = new AuthUser("google", "user-uid", "user@example.com");
        given(memberService.find(authUser)).willReturn(Optional.of(MemberFixture.member(2L, "user@example.com")));
        MockMvc withRealAdminCheck = mockMvcResolvingAdminWith(new AdminMemberArgumentResolver(memberService));

        withRealAdminCheck.perform(post(GRADE_URL, 10).param("limit", limit)
                        .requestAttr(FirebaseAuthInterceptor.AUTH_USER, authUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        then(ossIssueGradingService).should(never()).gradeRepo(anyLong(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"OSS_REPO_NOT_FOUND", "OSS_ISSUE_GRADING_IN_PROGRESS"})
    void gradeIssues_serviceRefuses_returnsThatErrorCodeAndStatus(ErrorCode errorCode) throws Exception {
        given(ossIssueGradingService.gradeRepo(10L, DEFAULT_LIMIT)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(post(GRADE_URL, 10))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/admin/oss/repos/10/issues/grade"));
    }

    private MockMvc mockMvcResolvingAdminWith(HandlerMethodArgumentResolver adminResolver) {
        return MockMvcBuilders.standaloneSetup(new AdminOssIssueGradingController(ossIssueGradingService))
                .setCustomArgumentResolvers(adminResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
