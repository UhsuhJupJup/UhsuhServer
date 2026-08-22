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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private static String extractToken(String html) {
        Matcher matcher = TOKEN_IN_LINK.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("확인메일에서 토큰을 찾지 못했습니다: " + html);
        }
        return matcher.group(1);
    }
}
