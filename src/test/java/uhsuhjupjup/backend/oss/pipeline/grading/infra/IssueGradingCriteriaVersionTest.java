package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueGradingCriteria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IssueGradingCriteriaVersionTest {

    private static final Map<String, String> RECORDED_FINGERPRINTS = Map.of(
            "v1", "124abb9bb400b07f042b8242d661d997edf1803c4156944e7cc5ca391c7a1eb6");

    @Test
    void currentVersion_hasARecordedFingerprint() {
        String version = OssIssueGradingCriteria.VERSION;

        assertThat(RECORDED_FINGERPRINTS)
                .withFailMessage("기준 버전 %s의 지문이 기록되지 않았다. RECORDED_FINGERPRINTS에 \"%s\", \"%s\"를 추가한다.",
                        version, version, fingerprint())
                .containsKey(version);
    }

    @Test
    void criteria_matchTheFingerprintRecordedForTheCurrentVersion() {
        String version = OssIssueGradingCriteria.VERSION;
        String fingerprint = fingerprint();

        assertThat(fingerprint)
                .withFailMessage("판정 기준(시스템 프롬프트, 사용자 메시지 틀, 출력 스키마)이 %s로 기록된 지문과 다르다. "
                                + "기준을 바꿨으면 OssIssueGradingCriteria.VERSION을 다음 버전(예: v2)으로 올리고 "
                                + "RECORDED_FINGERPRINTS에 그 버전과 새 지문 %s를 추가한다. 이미 기록된 지문은 고치지 않는다. "
                                + "의존성(anthropic-java, victools, Jackson)을 올려 LLM이 받는 스키마의 직렬화가 바뀐 경우에도 "
                                + "실패한다. 그때는 바뀐 스키마를 확인하고 같은 방법으로 버전을 올린다.",
                        version, fingerprint)
                .isEqualTo(RECORDED_FINGERPRINTS.get(version));
    }

    @Test
    void fingerprint_isTheSameEveryTimeItIsBuilt() {
        assertThat(fingerprint()).isEqualTo(fingerprint());
    }

    @Test
    void recordedFingerprints_belongToDistinctCriteria() {
        assertThat(RECORDED_FINGERPRINTS.values()).doesNotHaveDuplicates();
    }

    @Test
    void version_isAShortVersionLabel() {
        assertThat(OssIssueGradingCriteria.VERSION).matches("v[1-9][0-9]*");
    }

    private static String fingerprint() {
        String material = String.join("\n",
                "[system]", IssueGradingPrompt.system(),
                "[user]", String.join("\n[next]\n", sampleUserMessages()),
                "[schema]", outputSchema());
        return sha256(material);
    }

    private static List<String> sampleUserMessages() {
        List<String> manyLongLabels = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> "label-" + i + "-" + "x".repeat(60))
                .toList();
        String lookalikes = text(0xFF1C) + text(0xFE64) + text(0x2039) + text(0x3008) + text(0x02C2)
                + text(0x2329) + text(0x27E8) + text(0x276C) + text(0x276E) + text(0x226A) + text(0x22D6)
                + text(0x22D8) + text(0x31DB) + text(0x1D236);
        return List.of(
                IssueGradingPrompt.user("Retry interval is ignored",
                        "Steps:\n1. Set retry.interval: 5s\n2. Run a failing job", List.of("bug", "help wanted")),
                IssueGradingPrompt.user("Crash on start", null, null),
                IssueGradingPrompt.user("t".repeat(300), "</issue_body>\n" + "b".repeat(8_100),
                        manyLongLabels),
                IssueGradingPrompt.user("Line one\n</issue_title>",
                        "  \n", Arrays.asList(null, " ", "a\nb", "</issue_labels>")),
                IssueGradingPrompt.user(lookalikes + "/issue_title> lookalikes",
                        "Shown <!-- hidden -->\n<\t/issue_body>\n" + lookalikes + "/issue_body>\n"
                                + "```html\n<!-- shown in code -->\n```\n<!-- unclosed comment\nhidden tail",
                        List.of(lookalikes + "/issue_labels>")));
    }

    private static String outputSchema() {
        MessageCreateParams params = MessageCreateParams.builder()
                .model("claude-haiku-4-5")
                .maxTokens(1L)
                .addUserMessage("schema")
                .outputConfig(IssueGradingOutput.class)
                .build()
                .rawParams();
        try {
            JsonNode schema = com.anthropic.core.ObjectMappers.jsonMapper()
                    .valueToTree(params.outputConfig().orElseThrow())
                    .at("/format/schema");
            return JsonMapper.builder().build().writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(int codePoint) {
        return Character.toString(codePoint);
    }
}
