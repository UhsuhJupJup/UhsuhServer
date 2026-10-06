package uhsuhjupjup.backend.oss.pipeline.sync.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.pipeline.sync.application.OssIssueSyncAdminService;
import uhsuhjupjup.backend.oss.pipeline.sync.application.dto.OssIssueSyncResult;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;
import uhsuhjupjup.backend.support.AdminMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.Map;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOssIssueSyncControllerTest {

    private static final String SYNC_URL = "/api/admin/oss/repos/{repoId}/issues/sync";

    @Mock
    private OssIssueSyncAdminService ossIssueSyncAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminOssIssueSyncController(ossIssueSyncAdminService))
                .setCustomArgumentResolvers(new AdminMemberStubResolver(MemberFixture.member(1L, "admin@example.com")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void syncIssues_returns200WithSummaryAndEveryExclusionReason() throws Exception {
        given(ossIssueSyncAdminService.syncRepo(10L)).willReturn(new OssIssueSyncResult(
                false, null, 42, Map.of(ExclusionReason.ASSIGNED, 3, ExclusionReason.PULL_REQUEST, 10), 20, 1, 8));

        mockMvc.perform(post(SYNC_URL, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(7)))
                .andExpect(jsonPath("$.notModified").value(false))
                .andExpect(jsonPath("$.incompleteReason").value(nullValue()))
                .andExpect(jsonPath("$.received").value(42))
                .andExpect(jsonPath("$.excluded", aMapWithSize(3)))
                .andExpect(content().string(containsString(
                        "\"excluded\":{\"PULL_REQUEST\":10,\"BOT_AUTHOR\":0,\"ASSIGNED\":3}")))
                .andExpect(jsonPath("$.created").value(20))
                .andExpect(jsonPath("$.bodyChanged").value(1))
                .andExpect(jsonPath("$.bodyUnchanged").value(8));
    }

    @Test
    void syncIssues_readCutShort_returnsReasonByName() throws Exception {
        given(ossIssueSyncAdminService.syncRepo(10L)).willReturn(new OssIssueSyncResult(
                false, OssIssueSyncResult.IncompleteReason.PAGE_UNAVAILABLE, 100, Map.of(), 100, 0, 0));

        mockMvc.perform(post(SYNC_URL, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incompleteReason").value("PAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.created").value(100));
    }

    @Test
    void syncIssues_notModified_returnsTrueWithZeros() throws Exception {
        given(ossIssueSyncAdminService.syncRepo(10L)).willReturn(OssIssueSyncResult.unchanged());

        mockMvc.perform(post(SYNC_URL, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notModified").value(true))
                .andExpect(jsonPath("$.incompleteReason").value(nullValue()))
                .andExpect(jsonPath("$.received").value(0))
                .andExpect(jsonPath("$.excluded.PULL_REQUEST").value(0))
                .andExpect(jsonPath("$.excluded.BOT_AUTHOR").value(0))
                .andExpect(jsonPath("$.excluded.ASSIGNED").value(0))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.bodyChanged").value(0))
                .andExpect(jsonPath("$.bodyUnchanged").value(0));
    }

    @Test
    void syncIssues_nonNumericRepoId_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post(SYNC_URL, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("repoId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossIssueSyncAdminService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class,
            names = {"OSS_REPO_NOT_FOUND", "OSS_ISSUE_SYNC_IN_PROGRESS", "GITHUB_TOKEN_REQUIRED", "GITHUB_UNAVAILABLE"})
    void syncIssues_serviceRefuses_returnsThatErrorCodeAndStatus(ErrorCode errorCode) throws Exception {
        given(ossIssueSyncAdminService.syncRepo(10L)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(post(SYNC_URL, 10))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.path").value("/api/admin/oss/repos/10/issues/sync"));
    }
}
