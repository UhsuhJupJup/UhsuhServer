package uhsuhjupjup.backend.pipeline.matching.infra;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassificationException;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchTarget;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class WebClientKeywordClassifierTest {

    private static final String CLAUDE_PATH = "/v1/messages";
    private static final String GPT_PATH = "/v1/chat/completions";
    private static final String CLAUDE_OK = """
            {"content":[{"type":"tool_use","name":"report_keywords","input":{"keywords":["redis","없는키워드"]}}]}
            """;
    private static final String GPT_OK = """
            {"choices":[{"message":{"content":"{\\"keywords\\":[\\"kafka\\"]}"}}]}
            """;
    private static final MatchCatalog CATALOG = new MatchCatalog(List.of(
            new MatchTarget(1L, "redis", Set.of()),
            new MatchTarget(2L, "kafka", Set.of())));

    private StubLlmServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubLlmServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void Claude_도구_응답에서_카탈로그에_있는_키워드만_고른다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(200, CLAUDE_OK, Duration.ZERO));

        List<KeywordMatch> matches = claude(Duration.ofSeconds(2)).classify("제목", "본문", CATALOG).block();

        assertThat(matches).extracting(KeywordMatch::keywordId).containsExactly(1L);
        StubLlmServer.Received request = server.received().get(0);
        assertThat(request.headers().get("Anthropic-version")).containsExactly("2023-06-01");
        assertThat(request.body()).contains("\"tool_choice\"").contains("report_keywords");
    }

    @Test
    void 서버_오류는_한_번_재시도한다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(529, "{}", Duration.ZERO),
                new StubLlmServer.Reply(200, CLAUDE_OK, Duration.ZERO));

        List<KeywordMatch> matches = claude(Duration.ofSeconds(2)).classify("제목", "본문", CATALOG).block();

        assertThat(matches).hasSize(1);
        assertThat(server.count(CLAUDE_PATH)).isEqualTo(2);
    }

    @Test
    void 요청_오류는_재시도하지_않는다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(400, "{}", Duration.ZERO));

        assertThatThrownBy(() -> claude(Duration.ofSeconds(2)).classify("제목", "본문", CATALOG).block())
                .isInstanceOf(RuntimeException.class);
        assertThat(server.count(CLAUDE_PATH)).isEqualTo(1);
    }

    @Test
    void GPT_JSON_스키마_응답에서_키워드를_고른다() {
        server.reply(GPT_PATH, new StubLlmServer.Reply(200, GPT_OK, Duration.ZERO));

        List<KeywordMatch> matches = gpt().classify("제목", "본문", CATALOG).block();

        assertThat(matches).extracting(KeywordMatch::keywordId).containsExactly(2L);
        assertThat(server.received().get(0).body()).contains("json_schema");
    }

    @Test
    void Claude가_실패하면_GPT로_폴백한다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(500, "{}", Duration.ZERO));
        server.reply(GPT_PATH, new StubLlmServer.Reply(200, GPT_OK, Duration.ZERO));

        List<KeywordMatch> matches = resilient().classify("제목", "본문", CATALOG).block();

        assertThat(matches).extracting(KeywordMatch::keywordId).containsExactly(2L);
    }

    @Test
    void Claude가_시간_안에_답하지_않으면_GPT로_폴백한다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(200, CLAUDE_OK, Duration.ofSeconds(3)));
        server.reply(GPT_PATH, new StubLlmServer.Reply(200, GPT_OK, Duration.ZERO));

        List<KeywordMatch> matches = resilient(claude(Duration.ofMillis(300))).classify("제목", "본문", CATALOG)
                .block(Duration.ofSeconds(5));

        assertThat(matches).extracting(KeywordMatch::keywordId).containsExactly(2L);
    }

    @Test
    void 두_AI가_모두_실패하면_분류_예외를_낸다() {
        server.reply(CLAUDE_PATH, new StubLlmServer.Reply(500, "{}", Duration.ZERO));
        server.reply(GPT_PATH, new StubLlmServer.Reply(500, "{}", Duration.ZERO));

        assertThatThrownBy(() -> resilient().classify("제목", "본문", CATALOG).block())
                .isInstanceOf(KeywordClassificationException.class);
    }

    private ClaudeWebClientKeywordClassifier claude(Duration callTimeout) {
        return new ClaudeWebClientKeywordClassifier(server.baseUrl(), "claude-test", Duration.ofSeconds(1),
                callTimeout, 1, Duration.ofMillis(500));
    }

    private GptWebClientKeywordClassifier gpt() {
        return new GptWebClientKeywordClassifier(server.baseUrl(), "gpt-test", Duration.ofSeconds(1),
                Duration.ofSeconds(2), 1, Duration.ofMillis(500));
    }

    private ResilientAsyncKeywordClassifier resilient() {
        return resilient(claude(Duration.ofSeconds(2)));
    }

    @SuppressWarnings("unchecked")
    private ResilientAsyncKeywordClassifier resilient(ClaudeWebClientKeywordClassifier claude) {
        ObjectProvider<GptWebClientKeywordClassifier> gptProvider = mock(ObjectProvider.class);
        GptWebClientKeywordClassifier gpt = gpt();
        given(gptProvider.getIfAvailable()).willReturn(gpt);
        return new ResilientAsyncKeywordClassifier(CircuitBreakerRegistry.ofDefaults(), claude, gptProvider);
    }
}
