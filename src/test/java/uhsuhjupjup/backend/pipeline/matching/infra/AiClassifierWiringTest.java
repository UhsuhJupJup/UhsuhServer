package uhsuhjupjup.backend.pipeline.matching.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.support.SharedMySqlTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"claude.enabled=true", "gpt.enabled=true"})
@ActiveProfiles("test")
@Import(SharedMySqlTestConfiguration.class)
class AiClassifierWiringTest {

    @Autowired
    private KeywordClassifier keywordClassifier;

    @Autowired
    private AsyncKeywordClassifier asyncKeywordClassifier;

    @BeforeAll
    static void fakeKeys() {
        System.setProperty("anthropic.apiKey", "test-anthropic-key");
        System.setProperty("openai.apiKey", "test-openai-key");
    }

    @AfterAll
    static void clearKeys() {
        System.clearProperty("anthropic.apiKey");
        System.clearProperty("openai.apiKey");
    }

    @Test
    void AI를_켜면_배치는_동기_폴백_분류기를_노트_추천은_비동기_폴백_분류기를_쓴다() {
        assertThat(keywordClassifier).isInstanceOf(ResilientKeywordClassifier.class);
        assertThat(asyncKeywordClassifier).isInstanceOf(ResilientAsyncKeywordClassifier.class);
    }
}
