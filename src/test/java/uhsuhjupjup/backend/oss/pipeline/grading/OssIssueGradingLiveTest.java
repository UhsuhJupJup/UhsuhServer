package uhsuhjupjup.backend.oss.pipeline.grading;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uhsuhjupjup.backend.common.auth.AuthUser;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.domain.Role;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "oss.grading.claude.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
@EnabledIf("liveTestEnabled")
class OssIssueGradingLiveTest {

    private static final String LIVE_TEST_ENV = "OSS_GRADING_LIVE_TEST";
    private static final String GITHUB_TOKEN_ENV = "OSS_GITHUB_TOKEN";
    private static final String ANTHROPIC_KEY_ENV = "ANTHROPIC_API_KEY";
    private static final String OPENAI_KEY_ENV = "OPENAI_API_KEY";
    private static final String REPO_ENV = "OSS_GRADING_LIVE_REPO";
    private static final String LIMIT_ENV = "OSS_GRADING_LIVE_LIMIT";
    private static final String DEFAULT_REPO = "spring-projects/spring-boot";
    private static final String DEFAULT_LIMIT = "5";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String GRADING_LOGGER = "uhsuhjupjup.backend.oss.pipeline.grading";
    private static final String USAGE_LINE = "이슈 판정 응답 ";
    private static final String ISSUE_LINE = "오픈소스 이슈 판정 issueId=";
    private static final String HAIKU_4_5 = "claude-haiku-4-5";
    private static final String UNKNOWN = "none";
    private static final String NOTHING = "-";
    private static final Pattern FIELD = Pattern.compile("(\\w+)=(\\S+)");
    private static final BigDecimal INPUT_USD_PER_MILLION_TOKENS = new BigDecimal("1");
    private static final BigDecimal OUTPUT_USD_PER_MILLION_TOKENS = new BigDecimal("5");
    private static final BigDecimal CACHE_WRITE_RATE = new BigDecimal("1.25");
    private static final BigDecimal CACHE_READ_RATE = new BigDecimal("0.1");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final int USD_SCALE = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private final ListAppender<ILoggingEvent> gradingLogs = new ListAppender<>();
    private Logger gradingLogger;

    static boolean liveTestEnabled() {
        return "true".equals(System.getenv(LIVE_TEST_ENV)) && isSet(GITHUB_TOKEN_ENV) && isSet(ANTHROPIC_KEY_ENV);
    }

    @DynamicPropertySource
    static void gptFallbackOnlyWithItsKey(DynamicPropertyRegistry registry) {
        registry.add("oss.grading.gpt.enabled", () -> isSet(OPENAI_KEY_ENV));
    }

    @BeforeEach
    void setUp() {
        ossRepoRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        Member admin = Member.create("google", "admin-uid", "admin@example.com");
        ReflectionTestUtils.setField(admin, "role", Role.ADMIN);
        memberRepository.save(admin);
        given(firebaseTokenVerifier.verify(ADMIN_TOKEN))
                .willReturn(new AuthUser("google", "admin-uid", "admin@example.com", Instant.now()));
        gradingLogger = (Logger) LoggerFactory.getLogger(GRADING_LOGGER);
        gradingLogs.start();
        gradingLogger.addAppender(gradingLogs);
    }

    @AfterEach
    void tearDown() {
        gradingLogger.detachAppender(gradingLogs);
        gradingLogs.stop();
    }

    @Test
    void gradeIssuesOfARealRepo_printsEachVerdictWithItsTokensAndEstimatedCost() throws Exception {
        String fullName = envOr(REPO_ENV, DEFAULT_REPO);
        int limit = Integer.parseInt(envOr(LIMIT_ENV, DEFAULT_LIMIT));
        assertThat(limit).as(LIMIT_ENV + "는 1에서 20 사이여야 한다").isBetween(1, 20);

        Long repoId = register(fullName);
        JsonNode sync = adminCall(post("/api/admin/oss/repos/{repoId}/issues/sync", repoId));
        JsonNode run = adminCall(post("/api/admin/oss/repos/{repoId}/issues/grade", repoId)
                .param("limit", String.valueOf(limit)));
        List<Map<String, String>> issueLines = linesStartingWith(ISSUE_LINE);
        List<Usage> usages = linesStartingWith(USAGE_LINE).stream().map(Usage::of).toList();
        Map<String, List<Usage>> usagesByIssue = usages.stream()
                .collect(Collectors.groupingBy(Usage::issueId, LinkedHashMap::new, Collectors.toList()));
        Map<String, Grade> grades = grades(repoId);

        System.out.println("[LIVE] 레포 " + fullName + ", 판정 개수 " + limit
                + ", GPT 폴백 " + (isSet(OPENAI_KEY_ENV) ? "켬" : "끔"));
        System.out.println("[LIVE] 수집 요약 = " + sync);
        for (Map<String, String> line : issueLines) {
            String issueId = line.get("issueId");
            printIssue(line, grades.get(issueId), usagesByIssue.getOrDefault(issueId, List.of()));
        }
        System.out.println("[LIVE] 판정 요약 = " + run);
        printTotal(usages);
        printCache(usages);

        assertThat(run.path("selected").asInt()).as("수집한 이슈 중 판정할 이슈가 있어야 한다").isPositive();
        assertThat(run.path("graded").asInt()).as("실제 LLM이 판정한 이슈가 있어야 한다").isPositive();
        assertThat(usages).as("LLM 사용량 줄마다 그때 판정하던 이슈 id가 찍혀야 한다")
                .isNotEmpty()
                .allSatisfy(usage -> assertThat(usage.issueId()).matches("\\d+"));
        assertThat(usagesByIssue.keySet()).isSubsetOf(
                issueLines.stream().map(line -> line.get("issueId")).toList());
    }

    private static void printIssue(Map<String, String> line, Grade grade, List<Usage> usages) {
        String reason = line.getOrDefault("reason", NOTHING);
        System.out.println("[LIVE] ---- #" + line.get("number") + " (issueId=" + line.get("issueId") + ") "
                + line.get("outcome") + (reason.equals(NOTHING) ? "" : " " + reason));
        if (grade != null) {
            System.out.println("[LIVE]   난이도 " + orNone(grade.difficulty()) + ", 제외 " + orNone(grade.exclusion()));
            System.out.println("[LIVE]   근거: 문제 " + grade.problem() + ", 재현 " + grade.reproduction()
                    + ", 원인 " + grade.cause() + ", 수정 방향 " + grade.fixDirection()
                    + ", 관련 PR " + (grade.relatedPr() ? "있음" : "없음"));
            System.out.println("[LIVE]   이유(ko): " + grade.reasonKo());
            System.out.println("[LIVE]   이유(en): " + grade.reasonEn());
            System.out.println("[LIVE]   요약(ko): " + orNone(grade.summaryKo()));
            System.out.println("[LIVE]   요약(en): " + orNone(grade.summaryEn()));
            System.out.println("[LIVE]   답한 모델: " + grade.model());
        }
        if (usages.isEmpty()) {
            System.out.println("[LIVE]   LLM을 부르지 않음");
            return;
        }
        List<Usage> priced = haikuOf(usages);
        List<Usage> unpriced = othersOf(usages);
        if (!priced.isEmpty()) {
            System.out.println("[LIVE]   토큰(Haiku 4.5): " + describe(priced));
            System.out.println("[LIVE]   비용 어림: $" + costOf(priced).toPlainString());
        }
        if (!unpriced.isEmpty()) {
            System.out.println("[LIVE]   토큰(단가 모름, 비용에서 뺌): " + describe(unpriced));
        }
    }

    private static void printTotal(List<Usage> usages) {
        List<Usage> priced = haikuOf(usages);
        List<Usage> unpriced = othersOf(usages);
        if (priced.isEmpty()) {
            System.out.println("[LIVE] 합계(Haiku 4.5): 호출 없음");
        } else {
            BigDecimal total = costOf(priced);
            long issues = priced.stream().map(Usage::issueId).distinct().count();
            BigDecimal perIssue = total.divide(BigDecimal.valueOf(issues), USD_SCALE, RoundingMode.HALF_UP);
            System.out.println("[LIVE] 합계(Haiku 4.5): " + describe(priced) + ", 비용 어림 $" + total.toPlainString()
                    + ", Haiku를 부른 이슈 " + issues + "개, 이슈당 $" + perIssue.toPlainString());
        }
        System.out.println("[LIVE] 합계(단가 모름, 비용에서 뺌): " + (unpriced.isEmpty() ? "호출 없음" : describe(unpriced)));
        System.out.println("[LIVE] 어림 단가(Haiku 4.5, 100만 토큰당): 입력 $" + INPUT_USD_PER_MILLION_TOKENS
                + ", 출력 $" + OUTPUT_USD_PER_MILLION_TOKENS + ", 캐시 쓰기는 입력의 " + CACHE_WRITE_RATE
                + "배, 캐시 읽기는 " + CACHE_READ_RATE + "배. Haiku 4.5가 아닌 호출(GPT, 다른 Claude 모델)은 비용에 넣지 않는다");
    }

    private static void printCache(List<Usage> usages) {
        List<Usage> claude = usages.stream().filter(Usage::claude).toList();
        if (claude.size() < 2) {
            System.out.println("[LIVE] 캐시: Claude 호출이 " + claude.size() + "번뿐이라 캐시가 걸리는지 알 수 없음");
            return;
        }
        List<Usage> later = claude.subList(1, claude.size());
        long read = later.stream().filter(usage -> usage.cacheRead() > 0).count();
        if (read == 0) {
            System.out.println("[LIVE] 캐시가 걸리지 않음: Claude 두 번째 호출부터 " + later.size() + "번 모두 캐시 읽기 0"
                    + "(프롬프트가 모델의 최소 캐시 길이보다 짧을 수 있음. Haiku 4.5는 4,096토큰 미만이면 캐시하지 않음)");
            return;
        }
        System.out.println("[LIVE] 캐시 읽기 확인: Claude 두 번째 호출부터 " + later.size() + "번 중 " + read + "번이 캐시를 읽음");
    }

    private static List<Usage> haikuOf(List<Usage> usages) {
        return usages.stream().filter(Usage::haiku).toList();
    }

    private static List<Usage> othersOf(List<Usage> usages) {
        return usages.stream().filter(usage -> !usage.haiku()).toList();
    }

    private static String describe(List<Usage> usages) {
        String models = usages.stream().map(Usage::model).distinct().collect(Collectors.joining(", "));
        return "호출 " + usages.size() + "번(" + models + "), 입력 " + sum(usages, Usage::input)
                + ", 캐시 쓰기 " + sum(usages, Usage::cacheWrite) + ", 캐시 읽기 " + sum(usages, Usage::cacheRead)
                + ", 출력 " + sum(usages, Usage::output);
    }

    private static long sum(List<Usage> usages, ToLongFunction<Usage> tokens) {
        return usages.stream().mapToLong(tokens).sum();
    }

    private static BigDecimal costOf(List<Usage> usages) {
        BigDecimal input = BigDecimal.valueOf(sum(usages, Usage::input))
                .add(CACHE_WRITE_RATE.multiply(BigDecimal.valueOf(sum(usages, Usage::cacheWrite))))
                .add(CACHE_READ_RATE.multiply(BigDecimal.valueOf(sum(usages, Usage::cacheRead))));
        BigDecimal output = BigDecimal.valueOf(sum(usages, Usage::output));
        return input.multiply(INPUT_USD_PER_MILLION_TOKENS)
                .add(output.multiply(OUTPUT_USD_PER_MILLION_TOKENS))
                .divide(MILLION, USD_SCALE, RoundingMode.HALF_UP);
    }

    private static String orNone(String value) {
        return value == null ? "없음" : value;
    }

    private Long register(String fullName) throws Exception {
        return adminCall(post("/api/admin/oss/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", fullName))))
                .path("id").asLong();
    }

    private JsonNode adminCall(MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request.header(AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as("관리자 API 응답 " + body).isBetween(200, 299);
        return objectMapper.readTree(body);
    }

    private List<Map<String, String>> linesStartingWith(String prefix) {
        return gradingLogs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(prefix))
                .map(OssIssueGradingLiveTest::fieldsOf)
                .toList();
    }

    private static Map<String, String> fieldsOf(String message) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD.matcher(message);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    private Map<String, Grade> grades(Long repoId) {
        Map<String, Grade> grades = new HashMap<>();
        jdbcTemplate.query("""
                SELECT g.issue_id, g.difficulty, g.exclusion, g.problem, g.reproduction, g.cause, g.fix_direction,
                       g.related_pr, g.reason_ko, g.reason_en, g.summary_ko, g.summary_en, g.model
                FROM oss_issue_grade g
                JOIN oss_issue i ON i.id = g.issue_id
                WHERE i.repo_id = ?
                ORDER BY g.id
                """, row -> {
            grades.put(row.getString("issue_id"), new Grade(row.getString("difficulty"), row.getString("exclusion"),
                    row.getString("problem"), row.getString("reproduction"), row.getString("cause"),
                    row.getString("fix_direction"), row.getBoolean("related_pr"), row.getString("reason_ko"),
                    row.getString("reason_en"), row.getString("summary_ko"), row.getString("summary_en"),
                    row.getString("model")));
        }, repoId);
        return grades;
    }

    private static String envOr(String name, String fallback) {
        return isSet(name) ? System.getenv(name).strip() : fallback;
    }

    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private record Usage(String issueId, String model, boolean claude, long input, long output, long cacheWrite,
                         long cacheRead) {

        static Usage of(Map<String, String> fields) {
            long cachedByGpt = tokens(fields, "cached");
            return new Usage(fields.getOrDefault("issueId", UNKNOWN), fields.getOrDefault("model", UNKNOWN),
                    fields.containsKey("stopReason"), tokens(fields, "input") - cachedByGpt, tokens(fields, "output"),
                    tokens(fields, "cacheWrite"), tokens(fields, "cacheRead") + cachedByGpt);
        }

        boolean haiku() {
            return claude && model.startsWith(HAIKU_4_5);
        }

        private static long tokens(Map<String, String> fields, String name) {
            return Long.parseLong(fields.getOrDefault(name, "0"));
        }
    }

    private record Grade(String difficulty, String exclusion, String problem, String reproduction, String cause,
                         String fixDirection, boolean relatedPr, String reasonKo, String reasonEn, String summaryKo,
                         String summaryEn, String model) {
    }
}
