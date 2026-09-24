package uhsuhjupjup.backend.pipeline.matching.infra;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
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
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class ClaudeKeywordClassifier implements KeywordClassifier {

    private static final String MATCHED_VIA = "ai";
    private static final long MAX_TOKENS = 1024L;

    private final AnthropicClient anthropicClient;
    private final String model;

    ClaudeKeywordClassifier(AnthropicClient anthropicClient,
                            @Value("${claude.model:claude-haiku-4-5}") String model) {
        this.anthropicClient = anthropicClient;
        this.model = model;
    }

    @Override
    public List<KeywordMatch> classify(String title, String body, MatchCatalog catalog) {
        if (catalog.targets().isEmpty()) {
            return List.of();
        }
        TextBlockParam cachedSystem = TextBlockParam.builder()
                .text(ClassificationPrompt.system(catalog))
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();

        StructuredMessageCreateParams<ClassifierOutput> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(cachedSystem))
                .addUserMessage(ClassificationPrompt.user(title, body))
                .outputConfig(ClassifierOutput.class)
                .build();

        StructuredMessage<ClassifierOutput> message = anthropicClient.messages().create(params);
        Usage usage = message.usage();
        log.debug("분류 토큰 input={} cacheWrite={} cacheRead={}",
                usage.inputTokens(),
                usage.cacheCreationInputTokens().orElse(0L),
                usage.cacheReadInputTokens().orElse(0L));

        ClassifierOutput output = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(structured -> structured.text())
                .findFirst()
                .orElse(new ClassifierOutput(List.of()));

        return KeywordResolver.resolve(output.keywords(), catalog, MATCHED_VIA);
    }

    record ClassifierOutput(List<String> keywords) {
    }
}
