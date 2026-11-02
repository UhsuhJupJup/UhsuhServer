package uhsuhjupjup.backend.pipeline.matching.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@ConditionalOnProperty(name = "gpt.enabled", havingValue = "true")
class GptWebClientKeywordClassifier implements AsyncKeywordClassifier {

    private static final String MATCHED_VIA = "ai";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> RESPONSE_FORMAT = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                    "name", "keywords",
                    "strict", true,
                    "schema", Map.of(
                            "type", "object",
                            "properties", Map.of("keywords", Map.of("type", "array", "items", Map.of("type", "string"))),
                            "required", List.of("keywords"),
                            "additionalProperties", false)));

    private final WebClient webClient;
    private final LlmCallLimits limits;
    private final String model;

    GptWebClientKeywordClassifier(@Value("${gpt.base-url:https://api.openai.com}") String baseUrl,
                                  @Value("${gpt.model:gpt-4o-mini}") String model,
                                  @Value("${gpt.timeout.connect:PT5S}") Duration connectTimeout,
                                  @Value("${gpt.timeout.call:PT10S}") Duration callTimeout,
                                  @Value("${gpt.retry.max-retries:1}") int maxRetries,
                                  @Value("${gpt.retry.max-wait:PT2S}") Duration maxRetryWait) {
        this.limits = new LlmCallLimits(connectTimeout, callTimeout, maxRetries, maxRetryWait);
        Map<String, String> headers = new HashMap<>();
        LlmClients.openAiApiKey().ifPresent(apiKey -> headers.put("Authorization", "Bearer " + apiKey));
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
                "messages", List.of(
                        Map.of("role", "system", "content", ClassificationPrompt.system(catalog)),
                        Map.of("role", "user", "content", ClassificationPrompt.user(title, body))),
                "response_format", RESPONSE_FORMAT);
        Mono<JsonNode> call = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class);
        return LlmWebClients.withLimits(call, limits)
                .map(response -> KeywordResolver.resolve(keywords(response), catalog, MATCHED_VIA));
    }

    static List<String> keywords(JsonNode response) {
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            return List.of();
        }
        try {
            List<String> keywords = new ArrayList<>();
            JSON.readTree(content).path("keywords").forEach(keyword -> keywords.add(keyword.asText()));
            return keywords;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GPT 응답의 키워드 JSON을 읽지 못함");
        }
    }
}
