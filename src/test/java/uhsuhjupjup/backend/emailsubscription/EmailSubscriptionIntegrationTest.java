package uhsuhjupjup.backend.emailsubscription;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import uhsuhjupjup.backend.common.auth.FirebaseTokenVerifier;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriberRepository;
import uhsuhjupjup.backend.emailsubscription.infra.EmailSubscriptionRepository;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.pipeline.notification.application.EmailSender;
import uhsuhjupjup.backend.pipeline.notification.application.dto.EmailMessage;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 Redis(Testcontainers) + MockMvc로 비회원 이메일 구독 2단계 계약을 검증한다.
 * 확인 토큰의 발급→소비 왕복이 실제 Redis에서 일어난다(발급은 register, 소비는 confirm).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailSubscriptionIntegrationTest extends MySqlTestSupport {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([0-9a-fA-F-]{36})");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;
    @Autowired
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Autowired
    private KeywordRepository keywordRepository;
    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private EmailSender emailSender;
    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void 등록하면_확인메일_토큰으로_인증까지_동작하고_토큰은_1회용이다() throws Exception {
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));

        // 1) 로그인 없이 등록 → 202
        mockMvc.perform(post("/api/email-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"keywordIds\":[" + redis.getId() + "]}"))
                .andExpect(status().isAccepted());

        // 구독자·구독이 생기고 아직 미인증
        EmailSubscriber subscriber = emailSubscriberRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(subscriber.isVerified()).isFalse();
        assertThat(emailSubscriptionRepository.findByEmailSubscriberId(subscriber.getId())).hasSize(1);

        // 2) 확인메일에서 토큰 추출 (이 토큰은 실제 Redis에 발급되어 있다)
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("new@example.com");
        String token = extractToken(captor.getValue().htmlBody());

        // 3) confirm → 302 success + 인증 완료 (실제 Redis consume)
        mockMvc.perform(get("/api/email-subscriptions/confirm").param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("verify=success")));
        assertThat(emailSubscriberRepository.findByEmail("new@example.com").orElseThrow().isVerified()).isTrue();

        // 4) 같은 토큰 재사용 → 실패 (1회용, GETDEL)
        mockMvc.perform(get("/api/email-subscriptions/confirm").param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("verify=failed")));
    }

    @Test
    void 회원_이메일로_등록하면_409이고_구독자도_메일도_생기지_않는다() throws Exception {
        memberRepository.save(Member.create("google", "uid-x", "member@example.com"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));

        mockMvc.perform(post("/api/email-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\",\"keywordIds\":[" + redis.getId() + "]}"))
                .andExpect(status().isConflict());

        verify(emailSender, never()).send(any());
        assertThat(emailSubscriberRepository.existsByEmail("member@example.com")).isFalse();
    }

    @Test
    void 이메일_형식이_틀리면_400이고_메일도_안_보낸다() throws Exception {
        mockMvc.perform(post("/api/email-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"keywordIds\":[1]}"))
                .andExpect(status().isBadRequest());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 인증후_관리_매직링크로_구독을_조회하고_변경한다() throws Exception {
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        Keyword jpa = keywordRepository.save(Keyword.create("JPA"));

        // 등록 → 확인메일 토큰으로 인증
        register("manage@example.com", redis.getId());
        String verifyToken = extractToken(captureLastEmailHtml());
        mockMvc.perform(get("/api/email-subscriptions/confirm").param("token", verifyToken))
                .andExpect(status().isFound());

        // 관리 링크 요청 → 관리 토큰(실제 Redis 발급)
        mockMvc.perform(post("/api/email-subscriptions/manage-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manage@example.com\"}"))
                .andExpect(status().isAccepted());
        String manageToken = extractToken(captureLastEmailHtml());

        // 조회 → 현재 Redis
        mockMvc.perform(get("/api/email-subscriptions/manage").param("token", manageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("manage@example.com"))
                .andExpect(jsonPath("$.keywords[0].name").value("Redis"));

        // 변경 → JPA
        mockMvc.perform(put("/api/email-subscriptions/manage").param("token", manageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywordIds\":[" + jpa.getId() + "]}"))
                .andExpect(status().isNoContent());

        // 재조회 → JPA로 교체됨
        mockMvc.perform(get("/api/email-subscriptions/manage").param("token", manageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords.length()").value(1))
                .andExpect(jsonPath("$.keywords[0].name").value("JPA"));
    }

    @Test
    void 이미_인증된_이메일로_다시_등록하면_409() throws Exception {
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        register("dup2@example.com", redis.getId());
        String verifyToken = extractToken(captureLastEmailHtml());
        mockMvc.perform(get("/api/email-subscriptions/confirm").param("token", verifyToken))
                .andExpect(status().isFound());

        mockMvc.perform(post("/api/email-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup2@example.com\",\"keywordIds\":[" + redis.getId() + "]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 무효한_관리토큰으로_조회하면_404() throws Exception {
        mockMvc.perform(get("/api/email-subscriptions/manage").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound());
    }

    private void register(String email, Long keywordId) throws Exception {
        mockMvc.perform(post("/api/email-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"keywordIds\":[" + keywordId + "]}"))
                .andExpect(status().isAccepted());
    }

    private String captureLastEmailHtml() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(captor.capture());
        List<EmailMessage> sent = captor.getAllValues();
        return sent.get(sent.size() - 1).htmlBody();
    }

    private static String extractToken(String html) {
        Matcher matcher = TOKEN_IN_LINK.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("메일에서 토큰을 찾지 못했습니다: " + html);
        }
        return matcher.group(1);
    }
}
