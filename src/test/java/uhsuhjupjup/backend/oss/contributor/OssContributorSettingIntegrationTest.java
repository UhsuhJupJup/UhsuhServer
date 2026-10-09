package uhsuhjupjup.backend.oss.contributor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.contributor.infra.OssContributorSettingRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class OssContributorSettingIntegrationTest {

    private static final String URL = "/api/oss/me/settings";
    private static final String TOKEN = "contributor-token";
    private static final String EMAIL = "contributor@example.com";
    private static final String OTHER_TOKEN = "other-contributor-token";
    private static final String OTHER_EMAIL = "other-contributor@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OssContributorSettingRepository ossContributorSettingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Member contributor;
    private Member other;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAllInBatch();
        contributor = signUp(TOKEN, "contributor-uid", EMAIL);
        other = signUp(OTHER_TOKEN, "other-contributor-uid", OTHER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteAllInBatch();
    }

    @Test
    void mySettings_neverSaved_returnsDefaultsWithLoginEmailAndStoresNothing() throws Exception {
        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));

        assertThat(ossContributorSettingRepository.count()).isZero();
    }

    @Test
    void defaultsThenReplaceThenMySettings_returnsWhatWasSaved() throws Exception {
        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ko"));

        mockMvc.perform(replace(TOKEN, "en", false, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));

        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));

        assertThat(ossContributorSettingRepository.count()).isEqualTo(1);
        assertThat(storedRow(contributor)).containsExactly("EN", false, true);
    }

    @Test
    void replaceAgain_updatesTheSameRow() throws Exception {
        mockMvc.perform(replace(TOKEN, "en", false, true)).andExpect(status().isOk());

        mockMvc.perform(replace(TOKEN, "ko", true, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        assertThat(ossContributorSettingRepository.count()).isEqualTo(1);
        assertThat(storedRow(contributor)).containsExactly("KO", true, true);
    }

    @Test
    void replaceWithSameValuesTwice_answersTheSameAndKeepsOneRow() throws Exception {
        String first = mockMvc.perform(replace(TOKEN, "en", true, false))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(replace(TOKEN, "en", true, false))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(ossContributorSettingRepository.count()).isEqualTo(1);
        assertThat(storedRow(contributor)).containsExactly("EN", true, false);
    }

    @Test
    void replace_bothChannelsOff_isStored() throws Exception {
        mockMvc.perform(replace(TOKEN, "ko", false, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(false));

        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(false));

        assertThat(storedRow(contributor)).containsExactly("KO", false, false);
    }

    @Test
    void replace_notificationEmailInBody_isIgnored() throws Exception {
        String content = "{\"language\":\"ko\",\"emailEnabled\":true,\"pushEnabled\":false,"
                + "\"notificationEmail\":\"elsewhere@example.com\"}";

        mockMvc.perform(put(URL).header(AUTHORIZATION, bearer(TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));

        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));
        assertThat(memberRepository.findById(contributor.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void replace_missingValue_returns400AndStoresNothing() throws Exception {
        mockMvc.perform(put(URL).header(AUTHORIZATION, bearer(TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ko\",\"emailEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pushEnabled"));

        assertThat(ossContributorSettingRepository.count()).isZero();
    }

    @Test
    void withoutToken_mySettings_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void withoutToken_replace_returns401EvenWithInvalidBodyAndStoresNothing() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(ossContributorSettingRepository.count()).isZero();
    }

    @Test
    void settingsOfDifferentMembers_doNotMix() throws Exception {
        mockMvc.perform(replace(TOKEN, "en", false, true)).andExpect(status().isOk());

        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(OTHER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.notificationEmail").value(OTHER_EMAIL));

        mockMvc.perform(replace(OTHER_TOKEN, "ko", false, false)).andExpect(status().isOk());

        mockMvc.perform(get(URL).header(AUTHORIZATION, bearer(TOKEN)))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.notificationEmail").value(EMAIL));
        assertThat(storedRow(contributor)).containsExactly("EN", false, true);
        assertThat(storedRow(other)).containsExactly("KO", false, false);
    }

    @Test
    void deletingMember_deletesOnlyTheirSetting() throws Exception {
        mockMvc.perform(replace(TOKEN, "en", false, true)).andExpect(status().isOk());
        mockMvc.perform(replace(OTHER_TOKEN, "ko", true, true)).andExpect(status().isOk());

        memberRepository.deleteById(contributor.getId());

        assertThat(ossContributorSettingRepository.existsById(contributor.getId())).isFalse();
        assertThat(ossContributorSettingRepository.existsById(other.getId())).isTrue();
    }

    @Test
    void firstReplaceSentTwiceAtOnce_bothSucceedAndOneWholeRowRemains() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> korean = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(replace(TOKEN, "ko", true, false)).andReturn().getResponse();
            });
            Future<MockHttpServletResponse> english = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(replace(TOKEN, "en", false, true)).andReturn().getResponse();
            });
            start.countDown();

            MockHttpServletResponse koreanResponse = korean.get(30, TimeUnit.SECONDS);
            MockHttpServletResponse englishResponse = english.get(30, TimeUnit.SECONDS);

            assertThat(koreanResponse.getStatus()).isEqualTo(200);
            assertThat(englishResponse.getStatus()).isEqualTo(200);
            assertThat(JsonPath.<String>read(koreanResponse.getContentAsString(), "$.language")).isEqualTo("ko");
            assertThat(JsonPath.<String>read(englishResponse.getContentAsString(), "$.language")).isEqualTo("en");
        } finally {
            executor.shutdownNow();
        }
        assertThat(ossContributorSettingRepository.count()).isEqualTo(1);
        assertThat(storedRow(contributor)).isIn(List.of("KO", true, false), List.of("EN", false, true));
    }

    @Test
    void firstReplace_whenAnotherFirstSaveCommitsAfterItsRead_savesOnceMoreOverIt() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> otherFirstSave = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        INSERT INTO oss_contributor_setting (member_id, language, email_enabled, push_enabled)
                        VALUES (?, 'KO', TRUE, TRUE)
                        """, contributor.getId());
                inserted.countDown();
                awaitRelease(release);
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MockHttpServletResponse> replaced = executor.submit(
                    () -> mockMvc.perform(replace(TOKEN, "en", false, true)).andReturn().getResponse());
            awaitSettingInsertWaitingForLock();
            release.countDown();
            otherFirstSave.get(10, TimeUnit.SECONDS);

            MockHttpServletResponse response = replaced.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(JsonPath.<String>read(response.getContentAsString(), "$.language")).isEqualTo("en");
            assertThat(JsonPath.<Boolean>read(response.getContentAsString(), "$.emailEnabled")).isFalse();
            assertThat(JsonPath.<Boolean>read(response.getContentAsString(), "$.pushEnabled")).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(ossContributorSettingRepository.count()).isEqualTo(1);
        assertThat(storedRow(contributor)).containsExactly("EN", false, true);
    }

    private Member signUp(String token, String uid, String email) {
        Member member = memberRepository.save(Member.create("google", uid, email));
        given(firebaseTokenVerifier.verify(token)).willReturn(new AuthUser("google", uid, email, Instant.now()));
        return member;
    }

    private MockHttpServletRequestBuilder replace(String token, String language, boolean emailEnabled,
                                                  boolean pushEnabled) throws Exception {
        return put(URL)
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "language", language, "emailEnabled", emailEnabled, "pushEnabled", pushEnabled)));
    }

    private List<Object> storedRow(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT language, email_enabled, push_enabled FROM oss_contributor_setting WHERE member_id = ?",
                (rs, rowNum) -> List.of(rs.getString("language"), rs.getBoolean("email_enabled"),
                        rs.getBoolean("push_enabled")),
                member.getId());
    }

    private void awaitSettingInsertWaitingForLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbcTemplate.queryForList("SHOW FULL PROCESSLIST").stream()
                    .map(process -> String.valueOf(process.get("Info")).toLowerCase(Locale.ROOT))
                    .anyMatch(statement -> statement.contains("insert into oss_contributor_setting"));
            if (waiting) {
                return;
            }
            Thread.sleep(20);
        }
        fail("설정 INSERT가 다른 트랜잭션이 잡은 행을 기다리는 모습을 10초 안에 보지 못했습니다.");
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
