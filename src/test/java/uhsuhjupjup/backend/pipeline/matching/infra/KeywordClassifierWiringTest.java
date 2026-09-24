package uhsuhjupjup.backend.pipeline.matching.infra;

import com.anthropic.client.AnthropicClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KeywordClassifierWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(
                    Collaborators.class,
                    ClaudeKeywordClassifier.class,
                    SubstringKeywordClassifier.class,
                    ResilientKeywordClassifier.class));

    @Test
    void claude가_비활성이면_substring이_단독_분류기다() {
        runner.withPropertyValues("claude.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(KeywordClassifier.class);
            assertThat(context.getBean(KeywordClassifier.class)).isInstanceOf(SubstringKeywordClassifier.class);
            assertThat(context).doesNotHaveBean(ResilientKeywordClassifier.class);
        });
    }

    @Test
    void claude가_활성이면_resilient가_주_분류기고_substring은_빈이_아니다() {
        runner.withPropertyValues("claude.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ResilientKeywordClassifier.class);
            assertThat(context).doesNotHaveBean(SubstringKeywordClassifier.class);
            assertThat(context.getBean(KeywordClassifier.class)).isInstanceOf(ResilientKeywordClassifier.class);
        });
    }

    @Configuration
    static class Collaborators {

        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        KeywordMatcher keywordMatcher() {
            return mock(KeywordMatcher.class);
        }

        @Bean
        AnthropicClient anthropicClient() {
            return mock(AnthropicClient.class);
        }

        @Bean
        CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }
    }
}
