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
import uhsuhjupjup.backend.pipeline.matching.domain.MatchTarget;

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
                .text(systemPrompt(catalog))
                .cacheControl(CacheControlEphemeral.builder().build())
                .build();

        StructuredMessageCreateParams<ClassifierOutput> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(cachedSystem))
                .addUserMessage(userPrompt(title, body))
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

    private String systemPrompt(MatchCatalog catalog) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("너는 기술 블로그 글을 아래 구독 키워드로 분류한다.\n");
        prompt.append("이 글이 실제로 주제로 다루는 키워드만 고른다. 스쳐 언급되거나 무관하면 고르지 않는다.\n");
        prompt.append("이 글의 핵심을 가장 잘 나타내는 순서로, 최대 5개까지만 고른다.\n");
        prompt.append("해당하는 키워드가 하나도 없으면 빈 목록을 반환한다. 목록에 없는 키워드는 만들어내지 않는다.\n\n");
        prompt.append("키워드 목록:\n");
        for (MatchTarget target : catalog.targets()) {
            prompt.append("- ").append(target.lowerName());
            if (!target.lowerAliases().isEmpty()) {
                prompt.append(" (별칭: ").append(String.join(", ", target.lowerAliases())).append(")");
            }
            prompt.append("\n");
        }
        return prompt.toString();
    }

    private String userPrompt(String title, String body) {
        String safeBody = (body == null || body.isBlank()) ? "(본문 없음)" : body;
        return "제목: " + title + "\n\n본문: " + safeBody;
    }

    record ClassifierOutput(List<String> keywords) {
    }
}
