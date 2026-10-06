package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Evidence;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Exclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueGradingOutputTest {

    private static final String CLAUDE_MODEL = "claude-haiku-4-5";
    private static final String GPT_MODEL = "gpt-4o-mini";

    @Test
    void anthropic_acceptsTheOutputTypeWithACachedSystemPrompt() {
        StructuredMessageCreateParams<IssueGradingOutput> params = anthropicParams();

        JsonNode outputConfig = anthropicJson(params.rawParams().outputConfig().orElseThrow());
        assertThat(params.outputType()).isEqualTo(IssueGradingOutput.class);
        assertThat(outputConfig.at("/format/type").asText()).isEqualTo("json_schema");
        assertThat(params.rawParams().system().orElseThrow().asTextBlockParams())
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.text()).isEqualTo(IssueGradingPrompt.system());
                    assertThat(block.cacheControl()).isPresent();
                });
    }

    @Test
    void openAi_acceptsTheOutputTypeInStrictMode() {
        StructuredChatCompletionCreateParams<IssueGradingOutput> params = openAiParams();

        JsonNode responseFormat = openAiJson(params.rawParams().responseFormat().orElseThrow());
        assertThat(responseFormat.at("/type").asText()).isEqualTo("json_schema");
        assertThat(responseFormat.at("/json_schema/strict").asBoolean()).isTrue();
    }

    @Test
    void bothSdks_sendTheSameSchema() {
        assertThat(anthropicSchema()).isEqualTo(openAiSchema());
    }

    @Test
    void schema_listsTheEvidenceThenExclusionAndLevelThenTheTexts() {
        JsonNode schema = anthropicSchema();

        List<String> properties = new ArrayList<>();
        schema.get("properties").fieldNames().forEachRemaining(properties::add);
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(name -> required.add(name.asText()));
        assertThat(properties).containsExactly(
                "evidenceCause", "evidenceFixDirection", "evidenceProblem", "evidenceRelatedPr",
                "evidenceReproduction", "exclusion", "level", "reasonEn", "reasonKo", "summaryEn", "summaryKo");
        assertThat(required).isEqualTo(properties);
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void schema_limitsChoicesToTheDomainValues() {
        JsonNode schema = anthropicSchema();

        assertThat(enumValues(schema.at("/$defs/Evidence/enum"))).isEqualTo(names(OssIssueEvidence.values()));
        assertThat(Stream.of("evidenceCause", "evidenceFixDirection", "evidenceProblem", "evidenceReproduction"))
                .allSatisfy(field -> assertThat(schema.at("/properties/" + field + "/$ref").asText())
                        .isEqualTo("#/$defs/Evidence"));
        assertThat(enumValues(schema.at("/properties/exclusion/enum"))).isEqualTo(
                Stream.concat(Stream.of("NONE"), names(OssIssueGradeExclusion.values()).stream()).toList());
        assertThat(enumValues(schema.at("/properties/level/enum"))).isEqualTo(names(OssIssueDifficulty.values()));
        assertThat(schema.at("/properties/evidenceRelatedPr/type").asText()).isEqualTo("boolean");
    }

    @Test
    void schema_letsOnlyTheSummariesBeNull() {
        JsonNode properties = anthropicSchema().get("properties");

        assertThat(properties.at("/summaryEn/type")).hasToString("[\"string\",\"null\"]");
        assertThat(properties.at("/summaryKo/type")).hasToString("[\"string\",\"null\"]");
        assertThat(properties.at("/reasonEn/type").asText()).isEqualTo("string");
        assertThat(properties.at("/reasonKo/type").asText()).isEqualTo("string");
        assertThat(properties.at("/exclusion/type").asText()).isEqualTo("string");
        assertThat(properties.at("/level/type").asText()).isEqualTo("string");
    }

    @Test
    void output_readsChoicesIgnoringCaseAndLeavesUnknownOrMissingValuesForTheValidator() throws Exception {
        JsonMapper mapper = JsonMapper.builder().addModule(new Jdk8Module()).build();

        IssueGradingOutput output = mapper.readValue("""
                {"evidenceCause": "present", "evidenceFixDirection": "Partial", "evidenceProblem": "ABSENT",
                 "evidenceRelatedPr": true, "evidenceReproduction": "SOMETIMES", "exclusion": "none",
                 "level": "Easy", "reasonEn": "Reason.", "summaryEn": null, "summaryKo": "요약이다."}
                """, IssueGradingOutput.class);

        assertThat(output.evidenceCause()).isEqualTo(Evidence.PRESENT);
        assertThat(output.evidenceFixDirection()).isEqualTo(Evidence.PARTIAL);
        assertThat(output.evidenceProblem()).isEqualTo(Evidence.ABSENT);
        assertThat(output.evidenceRelatedPr()).isTrue();
        assertThat(output.evidenceReproduction()).isNull();
        assertThat(output.exclusion()).isEqualTo(Exclusion.NONE);
        assertThat(output.level()).isEqualTo(Level.EASY);
        assertThat(output.reasonKo()).isNull();
        assertThat(output.summaryEn()).isEmpty();
        assertThat(output.summaryKo()).contains("요약이다.");
    }

    private static StructuredMessageCreateParams<IssueGradingOutput> anthropicParams() {
        return MessageCreateParams.builder()
                .model(CLAUDE_MODEL)
                .maxTokens(1_024L)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(IssueGradingPrompt.system())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(IssueGradingPrompt.user("Retry interval is ignored", "Steps are below.", List.of()))
                .outputConfig(IssueGradingOutput.class)
                .build();
    }

    private static StructuredChatCompletionCreateParams<IssueGradingOutput> openAiParams() {
        return ChatCompletionCreateParams.builder()
                .model(GPT_MODEL)
                .addSystemMessage(IssueGradingPrompt.system())
                .addUserMessage(IssueGradingPrompt.user("Retry interval is ignored", "Steps are below.", List.of()))
                .responseFormat(IssueGradingOutput.class)
                .build();
    }

    private static JsonNode anthropicSchema() {
        return anthropicJson(anthropicParams().rawParams().outputConfig().orElseThrow()).at("/format/schema");
    }

    private static JsonNode openAiSchema() {
        return openAiJson(openAiParams().rawParams().responseFormat().orElseThrow()).at("/json_schema/schema");
    }

    private static JsonNode anthropicJson(Object value) {
        return com.anthropic.core.ObjectMappers.jsonMapper().valueToTree(value);
    }

    private static JsonNode openAiJson(Object value) {
        return com.openai.core.ObjectMappers.jsonMapper().valueToTree(value);
    }

    private static List<String> enumValues(JsonNode values) {
        List<String> names = new ArrayList<>();
        values.forEach(value -> names.add(value.asText()));
        return names;
    }

    private static List<String> names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toList();
    }
}
