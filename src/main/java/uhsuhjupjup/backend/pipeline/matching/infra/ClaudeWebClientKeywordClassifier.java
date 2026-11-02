package uhsuhjupjup.backend.pipeline.matching.infra;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uhsuhjupjup.backend.config.llm.LlmCallLimits;
import uhsuhjupjup.backend.config.llm.LlmClients;
import uhsuhjupjup.backend.config.llm.LlmWebClients;
import uhsuhjupjup.backend.pipeline.matching.application.AsyncKeywordClassifier;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordMatch;
import uhsuhjupjup.backend.pipeline.matching.domain.KeywordResolver;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "claude.enabled", havingValue = "true")
class ClaudeWebClientKeywordClassifier implements AsyncKeywordClassifier {

    private static final String MATCHED_VIA = "ai";
    private static final int MAX_TOKENS = 1024;
    private static final String API_VERSION = "2023-06-01";
    private static final String TOOL_NAME = "report_keywords";
    private static final Map<String, Object> TOOL = Map.of(
            "name", TOOL_NAME,
            "description", "글이 주제로 다루는 키워드 목록을 보고한다",
            "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("keywords", Map.of("type", "array", "items", Map.of("type", "string"))),
                    "required", List.of("keywords")));

    private final WebClient webClient;
    private final LlmCallLimits limits;
    private final String model;

    ClaudeWebClientKeywordClassifier(@Value("${claude.base-url:https://api.anthropic.com}") String baseUrl,
                                     @Value("${claude.model:claude-haiku-4-5}") String model,
                                     @Value("${claude.timeout.connect:PT5S}") Duration connectTimeout,
                                     @Value("${claude.timeout.call:PT10S}") Duration callTimeout,
                                     @Value("${claude.retry.max-retries:1}") int maxRetries,
                                     @Value("${claude.retry.max-wait:PT2S}") Duration maxRetryWait) {
        this.limits = new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait);
        Map<String, String> headers = new HashMap<>();
        headers.put("anthropic-version", API_VERSION);
        LlmClients.anthropicApiKey().ifPresent(apiKey -> headers.put("x-api-key", apiKey));
        this.webClient = LlmWebClients.create(baseUrl, limits, headers);
        this.model = model;
    }

    @Override
    public Mono<List<KeywordMatch>> classify(String title, String body, MatchCatalog catalog) {
        if (catalog.targets().isEmpty()) {
            return Mono.just(List.of());
        }
        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", List.of(Map.of(
                        "type", "text",
                        "text", ClassificationPrompt.system(catalog),
                        "cache_control", Map.of("type", "ephemeral"))),
                "messages", List.of(Map.of("role", "user", "content", ClassificationPrompt.user(title, body))),
                "tools", List.of(TOOL),
                "tool_choice", Map.of("type", "tool", "name", TOOL_NAME));
        Mono<JsonNode> call = webClient.post()
                .uri("/v1/messages")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class);
        return LlmWebClients.withLimits(call, limits)
                .map(response -> KeywordResolver.resolve(keywords(response), catalog, MATCHED_VIA));
    }

    static List<String> keywords(JsonNode response) {
        List<String> keywords = new ArrayList<>();
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                block.path("input").path("keywords").forEach(keyword -> keywords.add(keyword.asText()));
            }
        }
        return keywords;
    }
}
