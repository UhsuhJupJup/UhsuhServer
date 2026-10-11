package uhsuhjupjup.backend.pipeline.matching.infra;

import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KeywordClassifierClientQualifierTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("claude.enabled=true", "gpt.enabled=true")
            .withConfiguration(UserConfigurations.of(
                    Clients.class,
                    ClaudeKeywordClassifier.class,
                    GptKeywordClassifier.class));

    @Test
    void 다른_용도의_LLM_클라이언트가_있어도_분류기는_분류용_클라이언트를_주입받는다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(ReflectionTestUtils.getField(context.getBean(ClaudeKeywordClassifier.class), "anthropicClient"))
                    .isSameAs(context.getBean(AnthropicConfig.KEYWORD_CLASSIFIER_CLIENT));
            assertThat(ReflectionTestUtils.getField(context.getBean(GptKeywordClassifier.class), "openAiClient"))
                    .isSameAs(context.getBean(OpenAiConfig.KEYWORD_CLASSIFIER_CLIENT));
        });
    }

    @Configuration
    static class Clients {

        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean(AnthropicConfig.KEYWORD_CLASSIFIER_CLIENT)
        AnthropicClient keywordClassifierAnthropicClient() {
            return mock(AnthropicClient.class);
        }

        @Bean
        AnthropicClient otherAnthropicClient() {
            return mock(AnthropicClient.class);
        }

        @Bean(OpenAiConfig.KEYWORD_CLASSIFIER_CLIENT)
        OpenAIClient keywordClassifierOpenAiClient() {
            return mock(OpenAIClient.class);
        }

        @Bean
        OpenAIClient otherOpenAiClient() {
            return mock(OpenAIClient.class);
        }
    }
}
