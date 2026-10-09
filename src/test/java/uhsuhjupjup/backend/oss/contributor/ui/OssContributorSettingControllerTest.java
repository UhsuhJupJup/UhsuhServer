package uhsuhjupjup.backend.oss.contributor.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.common.auth.LoginMemberArgumentResolver;
import uhsuhjupjup.backend.common.exception.GlobalExceptionHandler;
import uhsuhjupjup.backend.member.application.MemberService;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.contributor.application.OssContributorSettingService;
import uhsuhjupjup.backend.oss.contributor.application.dto.OssContributorSettingResult;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.support.LoginMemberStubResolver;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OssContributorSettingControllerTest {

    private static final String URL = "/api/oss/me/settings";
    private static final String LOGIN_EMAIL = "user@example.com";
    private static final String REQUIRED = "필수 값입니다.";
    private static final String LANGUAGE_REASON = "ko, en 중 하나여야 합니다.";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OssContributorSettingService ossContributorSettingService;

    @Mock
    private MemberService memberService;

    private final Member loginMember = MemberFixture.member(1L, LOGIN_EMAIL);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OssContributorSettingController(ossContributorSettingService))
                .setCustomArgumentResolvers(new LoginMemberStubResolver(loginMember))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mySettings_returnsLanguageCodeChannelsAndLoginEmailOnly() throws Exception {
        given(ossContributorSettingService.getSetting(loginMember))
                .willReturn(new OssContributorSettingResult(OssContributorLanguage.KO, true, false, LOGIN_EMAIL));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.notificationEmail").value(LOGIN_EMAIL));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(OssContributorLanguage.class)
    void replace_everyLanguageCode_passesValuesToServiceAndReturnsSavedSetting(OssContributorLanguage language)
            throws Exception {
        given(ossContributorSettingService.replaceSetting(loginMember, language, false, true))
                .willReturn(new OssContributorSettingResult(language, false, true, LOGIN_EMAIL));

        mockMvc.perform(replace(body(language.code(), false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.language").value(language.code()))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.notificationEmail").value(LOGIN_EMAIL));
    }

    @Test
    void replace_bothChannelsOff_reachesServiceAsIs() throws Exception {
        given(ossContributorSettingService.replaceSetting(loginMember, OssContributorLanguage.KO, false, false))
                .willReturn(new OssContributorSettingResult(OssContributorLanguage.KO, false, false, LOGIN_EMAIL));

        mockMvc.perform(replace(body("ko", false, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(false));
    }

    @Test
    void replace_unknownFieldsLikeNotificationEmail_areIgnored() throws Exception {
        Map<String, Object> body = body("ko", true, false);
        body.put("notificationEmail", "other@example.com");
        body.put("theme", "dark");
        given(ossContributorSettingService.replaceSetting(loginMember, OssContributorLanguage.KO, true, false))
                .willReturn(new OssContributorSettingResult(OssContributorLanguage.KO, true, false, LOGIN_EMAIL));

        mockMvc.perform(replace(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEmail").value(LOGIN_EMAIL));

        then(ossContributorSettingService).should().replaceSetting(loginMember, OssContributorLanguage.KO, true, false);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"language", "emailEnabled", "pushEnabled"})
    void replace_missingValue_returns400WithThatFieldOnly(String field) throws Exception {
        Map<String, Object> body = body("ko", true, false);
        body.remove(field);

        mockMvc.perform(replace(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(REQUIRED));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"language", "emailEnabled", "pushEnabled"})
    void replace_nullValue_returns400WithThatFieldOnly(String field) throws Exception {
        Map<String, Object> body = body("ko", true, false);
        body.put(field, null);

        mockMvc.perform(replace(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(REQUIRED));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @Test
    void replace_emptyObject_returns400WithAllThreeFields() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        containsInAnyOrder("language", "emailEnabled", "pushEnabled")))
                .andExpect(jsonPath("$.fieldErrors[*].reason", containsInAnyOrder(REQUIRED, REQUIRED, REQUIRED)));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"KO", "En", "EN", "jp", "kor", "ko-KR", "ko_KR", "ko,en", "", " ", " ko", "en "})
    void replace_languageOutsideTwoLowercaseCodes_returns400WithLanguageFieldError(String language)
            throws Exception {
        mockMvc.perform(replace(body(language, true, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("language"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(LANGUAGE_REASON));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"\"yes\"", "\"on\"", "[]", "{}"})
    void replace_channelNotReadableAsBoolean_returns400MalformedRequest(String emailEnabled) throws Exception {
        String content = "{\"language\":\"ko\",\"emailEnabled\":" + emailEnabled + ",\"pushEnabled\":false}";

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @Test
    void replace_brokenJson_returns400MalformedRequest() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"ko\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @Test
    void replace_withoutBody_returns400MalformedRequest() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    @Test
    void mySettings_withoutLogin_returns401WithoutCallingService() throws Exception {
        withRealLoginResolver().perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        then(ossContributorSettingService).shouldHaveNoInteractions();
        then(memberService).shouldHaveNoInteractions();
    }

    @Test
    void replace_withoutLogin_returns401BeforeLookingAtTheBody() throws Exception {
        withRealLoginResolver().perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        then(ossContributorSettingService).shouldHaveNoInteractions();
    }

    private MockMvc withRealLoginResolver() {
        return MockMvcBuilders.standaloneSetup(new OssContributorSettingController(ossContributorSettingService))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder replace(Map<String, Object> body) throws Exception {
        return put(URL).contentType(MediaType.APPLICATION_JSON).content(OBJECT_MAPPER.writeValueAsString(body));
    }

    private static Map<String, Object> body(String language, boolean emailEnabled, boolean pushEnabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language", language);
        body.put("emailEnabled", emailEnabled);
        body.put("pushEnabled", pushEnabled);
        return body;
    }
}
