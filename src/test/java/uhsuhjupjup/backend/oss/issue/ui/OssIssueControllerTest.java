package uhsuhjupjup.backend.oss.issue.ui;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.oss.issue.application.OssIssueService;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDetailResult;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OssIssueControllerTest {

    private static final String URL = "/api/oss/issues/{issueId}";
    private static final Long ISSUE_ID = 501L;
    private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";
    private static final String REASON_KO = "재현 테스트와 원인 함수가 본문에 있다.";
    private static final String REASON_EN = "The body has a failing test and the faulty function.";
    private static final String SUMMARY_KO = "설정을 읽는 순서 때문에 재시도 간격이 무시된다.";
    private static final String SUMMARY_EN = "The retry interval is ignored because of the loading order.";

    @Mock
    private OssIssueService ossIssueService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OssIssueController(ossIssueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void detail_withoutLang_returnsIssueRepoVerdictAndTextsWithGithubUrl() throws Exception {
        given(ossIssueService.getDetail(ISSUE_ID, null)).willReturn(result(OssIssueLanguage.KO));

        mockMvc.perform(get(URL, ISSUE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.number").value(1284))
                .andExpect(jsonPath("$.title").value("Retry interval in the config file is ignored"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/acme/fastqueue/issues/1284"))
                .andExpect(jsonPath("$.githubCreatedAt").value("2026-10-05T09:12:44"))
                .andExpect(jsonPath("$.repo.id").value(10))
                .andExpect(jsonPath("$.repo.fullName").value("acme/fastqueue"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.evidence.problem").value("PRESENT"))
                .andExpect(jsonPath("$.evidence.reproduction").value("PARTIAL"))
                .andExpect(jsonPath("$.evidence.cause").value("ABSENT"))
                .andExpect(jsonPath("$.evidence.fixDirection").value("PRESENT"))
                .andExpect(jsonPath("$.evidence.relatedPr").value(true))
                .andExpect(jsonPath("$.lang").value("ko"))
                .andExpect(jsonPath("$.reason").value(REASON_KO))
                .andExpect(jsonPath("$.summary").value(SUMMARY_KO))
                .andExpect(jsonPath("$.gradedAt").value("2026-10-08T14:20:11"))
                .andExpect(jsonPath("$.repoId").doesNotExist())
                .andExpect(jsonPath("$.problem").doesNotExist())
                .andExpect(jsonPath("$.language").doesNotExist());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"ko, KO", "en, EN"})
    void detail_langParameter_choosesTheLanguageAndIsEchoed(String lang, OssIssueLanguage expected)
            throws Exception {
        given(ossIssueService.getDetail(ISSUE_ID, expected)).willReturn(result(expected));

        mockMvc.perform(get(URL, ISSUE_ID).param("lang", lang))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value(lang))
                .andExpect(jsonPath("$.reason").value(expected.choose(REASON_KO, REASON_EN)))
                .andExpect(jsonPath("$.summary").value(expected.choose(SUMMARY_KO, SUMMARY_EN)));
    }

    @Test
    void detail_emptyLang_isTreatedAsAbsent() throws Exception {
        given(ossIssueService.getDetail(ISSUE_ID, null)).willReturn(result(OssIssueLanguage.KO));

        mockMvc.perform(get(URL, ISSUE_ID).param("lang", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lang").value("ko"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"KO", "En", " ", " ko", "en ", "jp", "kor", "ko-KR", "ko_KR", "ko,en"})
    void detail_langOutsideTwoLowercaseCodes_returns400WithLangFieldError(String lang) throws Exception {
        mockMvc.perform(get(URL, ISSUE_ID).param("lang", lang))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lang"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(INVALID_FORMAT));

        then(ossIssueService).shouldHaveNoInteractions();
    }

    @Test
    void detail_repeatedLang_returns400WithLangFieldError() throws Exception {
        mockMvc.perform(get(URL, ISSUE_ID).param("lang", "ko", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lang"));

        then(ossIssueService).shouldHaveNoInteractions();
    }

    @Test
    void detail_issueNotFound_returns404IssueNotFound() throws Exception {
        given(ossIssueService.getDetail(99L, OssIssueLanguage.EN))
                .willThrow(new BusinessException(ErrorCode.OSS_ISSUE_NOT_FOUND));

        mockMvc.perform(get(URL, 99).param("lang", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OSS_ISSUE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("이슈를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/oss/issues/99"));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void detail_issueIdNotALong_returns400WithIssueIdFieldError(String issueId) throws Exception {
        mockMvc.perform(get("/api/oss/issues/" + issueId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("issueId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        then(ossIssueService).shouldHaveNoInteractions();
    }

    private static OssIssueDetailResult result(OssIssueLanguage language) {
        return new OssIssueDetailResult(ISSUE_ID, 1284, "Retry interval in the config file is ignored",
                LocalDateTime.of(2026, 10, 5, 9, 12, 44), 10L, "acme/fastqueue", OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PARTIAL, OssIssueEvidence.ABSENT, OssIssueEvidence.PRESENT,
                true, language, language.choose(REASON_KO, REASON_EN), language.choose(SUMMARY_KO, SUMMARY_EN),
                LocalDateTime.of(2026, 10, 8, 14, 20, 11));
    }
}
