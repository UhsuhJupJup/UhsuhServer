package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.client.AnthropicClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.config.llm.LlmRetryAbortedException;
import uhsuhjupjup.backend.config.llm.MockLlmServer;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.stall;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;

@Isolated
class ClaudeIssueGraderBindingTest {

    private static final String INVALID_VALUE = "::invalid::";
    private static final List<String> SDK_PROPERTIES = List.of(
            "anthropic.baseUrl", "anthropic.apiKey", "anthropic.authToken", "anthropic.webhookSigningKey");
    private static final String RATE_LIMITED = """
            {"type": "error", "error": {"type": "rate_limit_error", "message": "slow down"}}
            """;
    private static final String BAD_REQUEST = """
            {"type": "error", "error": {"type": "invalid_request_error", "message": "bad request"}}
            """;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private MockLlmServer server;
    private Map<String, String> propertiesBefore;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
        propertiesBefore = currentSdkProperties();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        assertThat(currentSdkProperties()).isEqualTo(propertiesBefore);
    }

    @Test
    void withoutEnv_createsNeitherTheGraderNorItsClient() {
        runnerWithEnv(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IssueGrader.class);
            assertThat(context).doesNotHaveBean(IssueGraderAnthropicConfig.ISSUE_GRADER_CLIENT);
        });
    }

    @Test
    void withoutEnv_resolvesTheDecidedSettings() {
        runnerWithEnv(Map.of()).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("oss.grading.claude.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("oss.grading.claude.model")).isEqualTo("claude-haiku-4-5");
            assertThat(environment.getProperty("oss.grading.claude.temperature")).isEqualTo("0");
            assertThat(environment.getProperty("oss.grading.claude.timeout.connect")).isEqualTo("PT5S");
            assertThat(environment.getProperty("oss.grading.claude.timeout.call")).isEqualTo("PT30S");
            assertThat(environment.getProperty("oss.grading.claude.retry.max-retries")).isEqualTo("1");
            assertThat(environment.getProperty("oss.grading.claude.retry.max-wait")).isEqualTo("PT2S");
        });
    }

    @Test
    void envValues_reachTheGrader() throws Exception {
        server.respondInOrder(stall());
        Map<String, Object> env = Map.of(
                "OSS_GRADING_CLAUDE_ENABLED", "true",
                "OSS_GRADING_CLAUDE_MODEL", "claude-binding-model",
                "OSS_GRADING_CLAUDE_TEMPERATURE", "0.7",
                "OSS_GRADING_CLAUDE_TIMEOUT_CONNECT", "PT1S",
                "OSS_GRADING_CLAUDE_TIMEOUT_CALL", "PT0.3S",
                "OSS_GRADING_CLAUDE_RETRY_MAX_RETRIES", "1");

        withFakeAnthropicEnvironment(() -> runnerWithEnv(env).run(context -> {
            IssueGrader grader = context.getBean(IssueGrader.class);

            assertThatThrownBy(() -> grader.grade("Title", "Body", List.of()))
                    .isInstanceOfSatisfying(IssueGradingException.class,
                            e -> assertThat(e.getReason()).isEqualTo(Reason.UNAVAILABLE));
            assertThat(server.sinceFirstRequest())
                    .isLessThan(Duration.ofMillis(300).multipliedBy(2).plus(Duration.ofSeconds(2)));
        }));

        JsonNode request = JSON.readTree(server.requestBody(0));
        assertThat(server.requestCount()).isEqualTo(2);
        assertThat(request.path("model").asText()).isEqualTo("claude-binding-model");
        assertThat(request.path("temperature").asDouble()).isEqualTo(0.7);
    }

    @Test
    void blankTemperatureEnv_leavesTemperatureOutOfTheRequest() throws Exception {
        server.respondInOrder(status(400, BAD_REQUEST));
        Map<String, Object> env = Map.of(
                "OSS_GRADING_CLAUDE_ENABLED", "true",
                "OSS_GRADING_CLAUDE_TEMPERATURE", "");

        withFakeAnthropicEnvironment(() -> runnerWithEnv(env).run(context ->
                assertThatThrownBy(() -> context.getBean(IssueGrader.class).grade("Title", "Body", List.of()))
                        .isInstanceOfSatisfying(IssueGradingException.class,
                                e -> assertThat(e.getReason()).isEqualTo(Reason.REJECTED))));

        assertThat(JSON.readTree(server.requestBody(0)).has("temperature")).isFalse();
    }

    @Test
    void maxWaitEnv_stopsTheRetryWhenTheApiAsksToWaitLonger() {
        server.respondInOrder(status(429, RATE_LIMITED, Map.of("Retry-After", "1")));
        Map<String, Object> env = Map.of(
                "OSS_GRADING_CLAUDE_ENABLED", "true",
                "OSS_GRADING_CLAUDE_RETRY_MAX_WAIT", "PT0.5S");

        withFakeAnthropicEnvironment(() -> runnerWithEnv(env).run(context ->
                assertThatThrownBy(() -> context.getBean(IssueGrader.class).grade("Title", "Body", List.of()))
                        .isInstanceOfSatisfying(IssueGradingException.class,
                                e -> assertThat(e.getCause()).isInstanceOf(LlmRetryAbortedException.class))));

        assertThat(server.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OSS_GRADING_CLAUDE_TEMPERATURE",
            "OSS_GRADING_CLAUDE_TIMEOUT_CONNECT",
            "OSS_GRADING_CLAUDE_TIMEOUT_CALL",
            "OSS_GRADING_CLAUDE_RETRY_MAX_RETRIES",
            "OSS_GRADING_CLAUDE_RETRY_MAX_WAIT"})
    void eachSettingEnvName_isReadAndAnInvalidValueStopsTheStart(String envName) {
        Map<String, Object> env = Map.of("OSS_GRADING_CLAUDE_ENABLED", "true", envName, INVALID_VALUE);

        withFakeAnthropicEnvironment(() -> runnerWithEnv(env).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().hasStackTraceContaining(INVALID_VALUE);
        }));
    }

    @Test
    void withAnotherAnthropicClient_theGraderStillGetsItsOwnClient() {
        withFakeAnthropicEnvironment(() -> runnerWithEnv(Map.of("OSS_GRADING_CLAUDE_ENABLED", "true"))
                .withUserConfiguration(AnotherAnthropicClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(AnthropicClient.class)).containsOnlyKeys(
                            AnotherAnthropicClient.NAME, IssueGraderAnthropicConfig.ISSUE_GRADER_CLIENT);
                    assertThat(ReflectionTestUtils.getField(context.getBean(IssueGrader.class), "anthropicClient"))
                            .isSameAs(context.getBean(IssueGraderAnthropicConfig.ISSUE_GRADER_CLIENT));
                }));
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
                .withConfiguration(UserConfigurations.of(IssueGraderAnthropicConfig.class, ClaudeIssueGrader.class));
    }

    private void withFakeAnthropicEnvironment(Runnable action) {
        Map<String, String> fakeEnvironment = Map.of(
                "anthropic.baseUrl", server.baseUrl(),
                "anthropic.apiKey", "fake-api-key",
                "anthropic.authToken", "fake-auth-token",
                "anthropic.webhookSigningKey", "fake-webhook-key");
        Map<String, String> previous = currentSdkProperties();
        try {
            fakeEnvironment.forEach(System::setProperty);
            action.run();
        } finally {
            previous.forEach(ClaudeIssueGraderBindingTest::restore);
        }
    }

    private static Map<String, String> currentSdkProperties() {
        Map<String, String> values = new HashMap<>();
        SDK_PROPERTIES.forEach(key -> values.put(key, System.getProperty(key)));
        return values;
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    @Configuration
    static class AnotherAnthropicClient {

        static final String NAME = "anotherAnthropicClient";

        @Bean(NAME)
        AnthropicClient anotherAnthropicClient() {
            return mock(AnthropicClient.class);
        }
    }
}
