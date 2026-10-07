package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;
import uhsuhjupjup.backend.config.llm.MockLlmServer;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGrader;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException;
import uhsuhjupjup.backend.oss.pipeline.grading.application.IssueGradingException.Reason;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static uhsuhjupjup.backend.config.llm.MockLlmServer.status;

class GptIssueGraderBindingTest {

    private static final String INVALID_VALUE = "::invalid::";
    private static final String BAD_REQUEST = """
            {"error": {"message": "bad request", "type": "invalid_request_error", "param": null, "code": null}}
            """;
    private static final LlmCallLimits LIMITS =
            new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(5), 1, Duration.ofSeconds(2));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<OpenAIClient> clients = new ArrayList<>();
    private MockLlmServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockLlmServer();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(OpenAIClient::close);
        server.stop();
    }

    @Test
    void withoutEnv_createsNeitherTheGraderNorItsClient() {
        runnerWithEnv(Map.of())
                .withConfiguration(UserConfigurations.of(IssueGraderOpenAiConfig.class, GptIssueGrader.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IssueGrader.class);
                    assertThat(context).doesNotHaveBean(IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT);
                });
    }

    @Test
    void withoutEnv_resolvesTheDecidedSettings() {
        runnerWithEnv(Map.of()).run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("oss.grading.gpt.enabled")).isEqualTo("false");
            assertThat(environment.getProperty("oss.grading.gpt.model")).isEqualTo("gpt-4o-mini");
            assertThat(environment.getProperty("oss.grading.gpt.temperature")).isEqualTo("0");
            assertThat(environment.getProperty("oss.grading.gpt.timeout.connect")).isEqualTo("PT5S");
            assertThat(environment.getProperty("oss.grading.gpt.timeout.call")).isEqualTo("PT30S");
            assertThat(environment.getProperty("oss.grading.gpt.retry.max-retries")).isEqualTo("1");
            assertThat(environment.getProperty("oss.grading.gpt.retry.max-wait")).isEqualTo("PT2S");
        });
    }

    @Test
    void clientSettingEnvValues_reachTheClientLimits() {
        Map<String, Object> env = Map.of(
                "OSS_GRADING_GPT_ENABLED", "true",
                "OSS_GRADING_GPT_TIMEOUT_CONNECT", "PT1S",
                "OSS_GRADING_GPT_TIMEOUT_CALL", "PT7S",
                "OSS_GRADING_GPT_RETRY_MAX_RETRIES", "2",
                "OSS_GRADING_GPT_RETRY_MAX_WAIT", "PT3S");

        assertThat(clientLimitsWith(env)).isEqualTo(
                new LlmCallLimits(Duration.ofSeconds(1), Duration.ofSeconds(7), 2, Duration.ofSeconds(3)));
    }

    @Test
    void withoutClientSettingEnv_theClientGetsTheDecidedLimits() {
        assertThat(clientLimitsWith(Map.of("OSS_GRADING_GPT_ENABLED", "true"))).isEqualTo(
                new LlmCallLimits(Duration.ofSeconds(5), Duration.ofSeconds(30), 1, Duration.ofSeconds(2)));
    }

    @Test
    void envValues_reachTheGrader() throws Exception {
        server.respondInOrder(status(400, BAD_REQUEST));
        Map<String, Object> env = Map.of(
                "OSS_GRADING_GPT_ENABLED", "true",
                "OSS_GRADING_GPT_MODEL", "gpt-binding-model",
                "OSS_GRADING_GPT_TEMPERATURE", "1.5");

        runnerWithTestClient(env).run(context ->
                assertThatThrownBy(() -> context.getBean(IssueGrader.class).grade("Title", "Body", List.of()))
                        .isInstanceOfSatisfying(IssueGradingException.class,
                                e -> assertThat(e.getReason()).isEqualTo(Reason.REJECTED)));

        JsonNode request = JSON.readTree(server.requestBody(0));
        assertThat(request.path("model").asText()).isEqualTo("gpt-binding-model");
        assertThat(request.path("temperature").asDouble()).isEqualTo(1.5);
    }

    @Test
    void blankTemperatureEnv_leavesTemperatureOutOfTheRequest() throws Exception {
        server.respondInOrder(status(400, BAD_REQUEST));
        Map<String, Object> env = Map.of(
                "OSS_GRADING_GPT_ENABLED", "true",
                "OSS_GRADING_GPT_TEMPERATURE", "");

        runnerWithTestClient(env).run(context ->
                assertThatThrownBy(() -> context.getBean(IssueGrader.class).grade("Title", "Body", List.of()))
                        .isInstanceOf(IssueGradingException.class));

        assertThat(JSON.readTree(server.requestBody(0)).has("temperature")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "OSS_GRADING_GPT_TIMEOUT_CONNECT",
            "OSS_GRADING_GPT_TIMEOUT_CALL",
            "OSS_GRADING_GPT_RETRY_MAX_RETRIES",
            "OSS_GRADING_GPT_RETRY_MAX_WAIT"})
    void eachClientSettingEnvName_isReadAndAnInvalidValueStopsTheStart(String envName) {
        Map<String, Object> env = Map.of("OSS_GRADING_GPT_ENABLED", "true", envName, INVALID_VALUE);

        runnerWithEnv(env)
                .withConfiguration(UserConfigurations.of(IssueGraderOpenAiConfig.class, GptIssueGrader.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().hasStackTraceContaining(INVALID_VALUE);
                });
    }

    @Test
    void invalidTemperatureEnv_stopsTheStart() {
        Map<String, Object> env = Map.of(
                "OSS_GRADING_GPT_ENABLED", "true",
                "OSS_GRADING_GPT_TEMPERATURE", INVALID_VALUE);

        runnerWithTestClient(env).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().hasStackTraceContaining(INVALID_VALUE);
        });
    }

    @Test
    void withAnotherOpenAiClient_theGraderStillGetsItsOwnClient() {
        runnerWithTestClient(Map.of("OSS_GRADING_GPT_ENABLED", "true"))
                .withBean("anotherOpenAiClient", OpenAIClient.class, () -> mock(OpenAIClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(OpenAIClient.class))
                            .containsOnlyKeys("anotherOpenAiClient", IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT);
                    assertThat(ReflectionTestUtils.getField(context.getBean(IssueGrader.class), "openAiClient"))
                            .isSameAs(context.getBean(IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT));
                });
    }

    private LlmCallLimits clientLimitsWith(Map<String, Object> env) {
        OpenAIClient client = mock(OpenAIClient.class);
        List<LlmCallLimits> requested = new ArrayList<>();
        try (MockedStatic<LlmClients> llmClients = mockStatic(LlmClients.class)) {
            llmClients.when(() -> LlmClients.openAi(any(LlmCallLimits.class))).thenAnswer(invocation -> {
                requested.add(invocation.getArgument(0));
                return client;
            });

            runnerWithEnv(env)
                    .withConfiguration(UserConfigurations.of(IssueGraderOpenAiConfig.class, GptIssueGrader.class))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(ReflectionTestUtils.getField(context.getBean(IssueGrader.class), "openAiClient"))
                                .isSameAs(client);
                    });
        }
        assertThat(requested).hasSize(1);
        return requested.get(0);
    }

    private ApplicationContextRunner runnerWithTestClient(Map<String, Object> env) {
        return runnerWithEnv(env)
                .withBean(IssueGraderOpenAiConfig.ISSUE_GRADER_CLIENT, OpenAIClient.class, this::testClient)
                .withConfiguration(UserConfigurations.of(GptIssueGrader.class));
    }

    private OpenAIClient testClient() {
        OpenAIClient client = server.openAiClient(LIMITS);
        clients.add(client);
        return client;
    }

    private ApplicationContextRunner runnerWithEnv(Map<String, Object> env) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env)))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()));
    }
}
