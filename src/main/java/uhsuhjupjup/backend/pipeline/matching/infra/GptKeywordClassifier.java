package uhsuhjupjup.backend.pipeline.matching.infra;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.pipeline.matching.application.KeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordResolver;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "gpt.enabled", havingValue = "true")
class GptKeywordClassifier implements KeywordClassifier {

    private static final String MATCHED_VIA = "ai";

    private final OpenAIClient openAiClient;
    private final String model;

    GptKeywordClassifier(OpenAIClient openAiClient,
                         @Value("${gpt.model:gpt-4o-mini}") String model) {
        this.openAiClient = openAiClient;
        this.model = model;
    }

    @Override
    public List<KeywordMatch> classify(String title, String body, MatchCatalog catalog) {
        if (catalog.targets().isEmpty()) {
            return List.of();
        }
        StructuredChatCompletionCreateParams<ClassifierOutput> params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(ClassificationPrompt.system(catalog))
                .addUserMessage(ClassificationPrompt.user(title, body))
                .responseFormat(ClassifierOutput.class)
                .build();

        ClassifierOutput output = openAiClient.chat().completions().create(params).choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .findFirst()
                .orElseGet(ClassifierOutput::new);

        return KeywordResolver.resolve(output.keywords, catalog, MATCHED_VIA);
    }

    static class ClassifierOutput {
        public List<String> keywords = List.of();
    }
}
