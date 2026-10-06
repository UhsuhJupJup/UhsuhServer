package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uhsuhjupjup.backend.oss.issue.domain.OssIssue;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueEvidence;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGrade;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueGradeExclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.InvalidIssueGradingOutputException.Violation;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.InvalidIssueGradingOutputException.Violation.Kind;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Evidence;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Exclusion;
import uhsuhjupjup.backend.oss.pipeline.grading.infra.IssueGradingOutput.Level;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(OutputCaptureExtension.class)
class IssueGradingOutputValidatorTest {

    private static final String BUG = "🐛";
    private static final String SOURCE_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String NO_BREAK_SPACE = Character.toString(0x00A0);
    private static final String EM_SPACE = Character.toString(0x2003);
    private static final String ZERO_WIDTH_SPACE = Character.toString(0x200B);
    private static final String WORD_JOINER = Character.toString(0x2060);
    private static final String BYTE_ORDER_MARK = Character.toString(0xFEFF);

    @Test
    void validate_completeOutput_becomesTheVerdict() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().build());

        assertThat(verdict).isEqualTo(new OssIssueVerdict(OssIssueDifficulty.MEDIUM,
                OssIssueEvidence.PRESENT, OssIssueEvidence.PRESENT, OssIssueEvidence.ABSENT, OssIssueEvidence.PARTIAL,
                true, null,
                "재현 절차는 있지만 원인이 없다.", "Steps are given but the cause is missing.",
                "종료 훅 순서 때문에 워커가 남는다.", "Workers linger because of the shutdown hook order."));
    }

    @ParameterizedTest
    @CsvSource({"evidenceCause, cause", "evidenceFixDirection, fixDirection",
            "evidenceProblem, problem", "evidenceReproduction, reproduction"})
    void validate_eachEvidenceField_landsOnItsOwnVerdictField(String outputField, String verdictField) {
        OutputBuilder output = output()
                .evidence(Evidence.ABSENT, Evidence.ABSENT, Evidence.ABSENT, Evidence.ABSENT)
                .evidence(outputField, Evidence.PRESENT);

        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output.build());

        assertThat(Stream.of("cause", "fixDirection", "problem", "reproduction"))
                .allSatisfy(field -> assertThat(evidenceOf(verdict, field)).isEqualTo(
                        field.equals(verdictField) ? OssIssueEvidence.PRESENT : OssIssueEvidence.ABSENT));
    }

    @ParameterizedTest
    @EnumSource(OssIssueEvidence.class)
    void validate_evidenceValue_mapsToTheSameDomainValue(OssIssueEvidence evidence) {
        Evidence answer = Evidence.valueOf(evidence.name());

        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().evidence(answer, answer, answer, answer).build());

        assertThat(List.of(verdict.cause(), verdict.fixDirection(), verdict.problem(), verdict.reproduction()))
                .containsOnly(evidence);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validate_relatedPr_isKept(boolean relatedPr) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().relatedPr(relatedPr).build());

        assertThat(verdict.relatedPr()).isEqualTo(relatedPr);
    }

    @ParameterizedTest
    @EnumSource(OssIssueDifficulty.class)
    void validate_level_becomesTheSameDifficulty(OssIssueDifficulty difficulty) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT)
                .level(Level.valueOf(difficulty.name()))
                .build());

        assertThat(verdict.difficulty()).isEqualTo(difficulty);
    }

    @ParameterizedTest
    @CsvSource({"evidenceCause, PARTIAL", "evidenceFixDirection, ABSENT",
            "evidenceProblem, ABSENT", "evidenceReproduction, PARTIAL"})
    void validate_easyWithAnElementNotPresent_isLoweredToMediumAndLogged(String field, Evidence value,
                                                                        CapturedOutput logs) {
        String reason = "LEAKED-REASON-TEXT";
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT)
                .evidence(field, value)
                .level(Level.EASY)
                .text("reasonEn", reason)
                .build());

        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.MEDIUM);
        assertThat(logs)
                .contains("이슈 판정 난이도를 EASY에서 MEDIUM으로 낮춤, PRESENT가 아닌 근거: " + field + "=" + value)
                .doesNotContain(reason);
    }

    @Test
    void validate_easyWithSeveralElementsNotPresent_namesEachOfThemInTheLog(CapturedOutput logs) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.ABSENT, Evidence.PARTIAL, Evidence.PRESENT, Evidence.PRESENT)
                .level(Level.EASY)
                .build());

        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.MEDIUM);
        assertThat(logs).contains("PRESENT가 아닌 근거: evidenceCause=ABSENT, evidenceFixDirection=PARTIAL");
    }

    @Test
    void validate_easyWithAllElementsPresent_staysEasyWithoutLog(CapturedOutput logs) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT)
                .level(Level.EASY)
                .build());

        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.EASY);
        assertThat(logs).doesNotContain("이슈 판정 난이도를");
    }

    @ParameterizedTest
    @EnumSource(value = Level.class, names = {"MEDIUM", "HARD"})
    void validate_mediumOrHard_staysWhateverTheEvidence(Level level, CapturedOutput logs) {
        OssIssueVerdict withoutEvidence = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.ABSENT, Evidence.ABSENT, Evidence.ABSENT, Evidence.ABSENT).level(level).build());
        OssIssueVerdict withAllEvidence = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT).level(level).build());

        assertThat(List.of(withoutEvidence.difficulty(), withAllEvidence.difficulty()))
                .containsOnly(OssIssueDifficulty.valueOf(level.name()));
        assertThat(logs).doesNotContain("이슈 판정 난이도를");
    }

    @Test
    void validate_excludedEasyWithAnElementNotPresent_isLoweredToMediumToo() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .evidence(Evidence.PRESENT, Evidence.PRESENT, Evidence.PRESENT, Evidence.ABSENT)
                .exclusion(Exclusion.DUPLICATE)
                .level(Level.EASY)
                .build());

        assertThat(verdict.exclusion()).isEqualTo(OssIssueGradeExclusion.DUPLICATE);
        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.MEDIUM);
    }

    @Test
    void validate_exclusionNone_meansNotExcluded() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().exclusion(Exclusion.NONE).build());

        assertThat(verdict.exclusion()).isNull();
    }

    @ParameterizedTest
    @EnumSource(OssIssueGradeExclusion.class)
    void validate_exclusion_becomesTheSameDomainExclusion(OssIssueGradeExclusion exclusion) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().exclusion(Exclusion.valueOf(exclusion.name())).build());

        assertThat(verdict.exclusion()).isEqualTo(exclusion);
    }

    @Test
    void validate_excludedIssue_keepsTheLevelItWasGiven() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().exclusion(Exclusion.SPAM).level(Level.HARD).build());

        assertThat(verdict.exclusion()).isEqualTo(OssIssueGradeExclusion.SPAM);
        assertThat(verdict.difficulty()).isEqualTo(OssIssueDifficulty.HARD);
    }

    @Test
    void validate_excludedWithoutSummaries_isAcceptedWithoutSummaries() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().exclusion(Exclusion.QUESTION).summaries(null, null).build());

        assertThat(verdict.summaryEn()).isNull();
        assertThat(verdict.summaryKo()).isNull();
    }

    @Test
    void validate_excludedWithBothSummaries_keepsThem() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().exclusion(Exclusion.DUPLICATE).summaries("Same as #12.", "12번과 같다.").build());

        assertThat(verdict.summaryEn()).isEqualTo("Same as #12.");
        assertThat(verdict.summaryKo()).isEqualTo("12번과 같다.");
    }

    @ParameterizedTest
    @CsvSource(value = {"Summary., null, summaryKo", "null, 요약이다., summaryEn"}, nullValues = "null")
    void validate_excludedWithASummaryInOneLanguage_isRejectedAsUnpaired(String summaryEn, String summaryKo,
                                                                          String missingField) {
        assertThat(violationsOf(output().exclusion(Exclusion.SPAM).summaries(summaryEn, summaryKo)))
                .containsExactly(tuple(missingField, Kind.UNPAIRED));
    }

    @ParameterizedTest
    @MethodSource("emptySummaryPairs")
    void validate_excludedWithBothSummariesEmpty_leavesThemOut(String summaryEn, String summaryKo) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(
                output().exclusion(Exclusion.SPAM).summaries(summaryEn, summaryKo).build());

        assertThat(verdict.summaryEn()).isNull();
        assertThat(verdict.summaryKo()).isNull();
    }

    private static Stream<Arguments> emptySummaryPairs() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("   ", null),
                Arguments.of(null, "\n"),
                Arguments.of(NO_BREAK_SPACE, ZERO_WIDTH_SPACE));
    }

    @Test
    void validate_excludedWithOneEmptySummary_isRejectedAsBlank() {
        assertThat(violationsOf(output().exclusion(Exclusion.SPAM).summaries("", "광고 글이다.")))
                .containsExactly(tuple("summaryEn", Kind.BLANK));
    }

    @Test
    void validate_notExcludedWithEmptySummaries_isRejectedAsBlank() {
        assertThat(violationsOf(output().exclusion(Exclusion.NONE).summaries("", " ")))
                .containsExactly(tuple("summaryEn", Kind.BLANK), tuple("summaryKo", Kind.BLANK));
    }

    @ParameterizedTest
    @CsvSource(value = {"Summary., null, summaryKo", "null, 요약이다., summaryEn"}, nullValues = "null")
    void validate_notExcludedWithASummaryInOneLanguage_isRejectedForTheMissingOne(String summaryEn,
                                                                                  String summaryKo,
                                                                                  String missingField) {
        assertThat(violationsOf(output().exclusion(Exclusion.NONE).summaries(summaryEn, summaryKo)))
                .containsExactly(tuple(missingField, Kind.MISSING));
    }

    @Test
    void validate_notExcludedWithoutSummaries_isRejectedForBothLanguages() {
        assertThat(violationsOf(output().exclusion(Exclusion.NONE).summaries(null, null)))
                .containsExactly(tuple("summaryEn", Kind.MISSING), tuple("summaryKo", Kind.MISSING));
    }

    @ParameterizedTest
    @ValueSource(strings = {"evidenceCause", "evidenceFixDirection", "evidenceProblem", "evidenceRelatedPr",
            "evidenceReproduction", "exclusion", "level", "reasonEn", "reasonKo"})
    void validate_missingRequiredField_isRejectedNamingTheField(String field) {
        assertThat(violationsOf(output().without(field))).containsExactly(tuple(field, Kind.MISSING));
    }

    @ParameterizedTest
    @MethodSource("blankTexts")
    void validate_emptyOrWhitespaceText_isRejectedAsBlank(String field, String blank) {
        assertThat(violationsOf(output().text(field, blank))).containsExactly(tuple(field, Kind.BLANK));
    }

    private static Stream<Arguments> blankTexts() {
        return Stream.of("reasonEn", "reasonKo", "summaryEn", "summaryKo")
                .flatMap(field -> Stream.of("", "   ", "\t\r\n ", NO_BREAK_SPACE, ZERO_WIDTH_SPACE, WORD_JOINER,
                                BYTE_ORDER_MARK, NO_BREAK_SPACE + ZERO_WIDTH_SPACE + " " + WORD_JOINER + BYTE_ORDER_MARK)
                        .map(blank -> Arguments.of(field, blank)));
    }

    @Test
    void validate_texts_areStrippedOfSurroundingWhitespace() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .text("reasonEn", "  Reason.\n").text("reasonKo", "\t이유다. ")
                .summaries(" Summary. ", "\n요약이다.\n").build());

        assertThat(List.of(verdict.reasonEn(), verdict.reasonKo(), verdict.summaryEn(), verdict.summaryKo()))
                .containsExactly("Reason.", "이유다.", "Summary.", "요약이다.");
    }

    @Test
    void validate_texts_areStrippedOfUnicodeSpacesAndInvisibleCharacters() {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output()
                .text("reasonEn", EM_SPACE + "Reason." + EM_SPACE)
                .text("reasonKo", NO_BREAK_SPACE + "이유다." + ZERO_WIDTH_SPACE)
                .summaries(BYTE_ORDER_MARK + "Summary." + WORD_JOINER, EM_SPACE + "요약이다." + NO_BREAK_SPACE)
                .build());

        assertThat(List.of(verdict.reasonEn(), verdict.reasonKo(), verdict.summaryEn(), verdict.summaryKo()))
                .containsExactly("Reason.", "이유다.", "Summary.", "요약이다.");
    }

    @ParameterizedTest
    @MethodSource("textsWithUrlOrEmail")
    void validate_textWithUrlOrEmail_isRejected(String field, String text) {
        assertThat(violationsOf(output().text(field, text))).containsExactly(tuple(field, Kind.URL_OR_EMAIL));
    }

    private static Stream<Arguments> textsWithUrlOrEmail() {
        return Stream.of("reasonEn", "reasonKo", "summaryEn", "summaryKo")
                .flatMap(field -> Stream.of(
                                "See https://example.com/issues/1 for the logs.",
                                "The docs live at HTTP://EXAMPLE.COM now.",
                                "Visit www.example.com first.",
                                "자세한 내용은www.example.com에 있다.",
                                "Contact dev@example.com for access.",
                                "문의는 first.last+tag@mail.example.co.kr로 한다.")
                        .map(text -> Arguments.of(field, text)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "@Transactional is ignored on private methods.",
            "Use a short name, e.g. retry.",
            "Fixed in v1.2.3 by editing config.yml.",
            "Set user.name before running.",
            "회원@관리자 권한이 필요하다.",
            "Install pkg@1.2.3 and @types/node first.",
            "awww. That was a typo."})
    void validate_textThatOnlyLooksLikeAnAddress_isAccepted(String text) {
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().text("summaryEn", text).build());

        assertThat(verdict.summaryEn()).isEqualTo(text);
    }

    @Test
    void validate_urlRejection_doesNotQuoteTheUrl() {
        OutputBuilder output = output().text("reasonEn", "Read https://evil.example/steal first.");

        assertThatThrownBy(() -> IssueGradingOutputValidator.validate(output.build()))
                .hasMessage("판정 출력 거절: reasonEn URL_OR_EMAIL")
                .hasMessageNotContaining("evil");
    }

    @ParameterizedTest
    @ValueSource(strings = {"reasonEn", "reasonKo"})
    void validate_reasonOf500CodePoints_isAccepted(String field) {
        String reason = BUG.repeat(500);

        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().text(field, "  " + reason + "  ").build());

        assertThat(reason.length()).isEqualTo(1_000);
        assertThat(field.equals("reasonEn") ? verdict.reasonEn() : verdict.reasonKo()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reasonEn", "reasonKo"})
    void validate_reasonOver500CodePoints_isRejectedAsTooLong(String field) {
        assertThat(violationsOf(output().text(field, "가".repeat(500) + BUG)))
                .containsExactly(tuple(field, Kind.TOO_LONG));
    }

    @Test
    void validate_summariesOf1500CodePoints_areAccepted() {
        String summary = "가".repeat(1_499) + BUG;

        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output().summaries(summary, summary).build());

        assertThat(verdict.summaryEn()).isEqualTo(summary);
        assertThat(verdict.summaryKo()).isEqualTo(summary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"summaryEn", "summaryKo"})
    void validate_summaryOver1500CodePoints_isRejectedAsTooLong(String field) {
        assertThat(violationsOf(output().text(field, BUG.repeat(1_501))))
                .containsExactly(tuple(field, Kind.TOO_LONG));
    }

    @Test
    void validate_rejection_listsEveryBrokenFieldWithoutQuotingTheOutput() {
        String leakedText = "LEAKED-ISSUE-TEXT ";
        OutputBuilder output = output()
                .without("level")
                .text("reasonKo", leakedText.repeat(40))
                .text("reasonEn", " ")
                .summaries(leakedText, null);

        assertThatThrownBy(() -> IssueGradingOutputValidator.validate(output.build()))
                .isInstanceOfSatisfying(InvalidIssueGradingOutputException.class, e -> {
                    assertThat(e.violations()).extracting(Violation::field, Violation::kind).containsExactly(
                            tuple("level", Kind.MISSING),
                            tuple("reasonEn", Kind.BLANK),
                            tuple("reasonKo", Kind.TOO_LONG),
                            tuple("summaryKo", Kind.MISSING));
                    assertThat(e.getMessage())
                            .contains("level MISSING", "reasonEn BLANK", "reasonKo TOO_LONG 719/500", "summaryKo MISSING")
                            .doesNotContain("LEAKED");
                });
    }

    @ParameterizedTest
    @MethodSource("acceptedShapes")
    void validate_acceptedVerdict_isAlwaysAcceptedByOssIssueGrade(Exclusion exclusion, Level level,
                                                                   boolean withSummaries) {
        String longest = BUG.repeat(1_500);
        OutputBuilder output = output().exclusion(exclusion).level(level)
                .text("reasonEn", BUG.repeat(500)).text("reasonKo", " " + "가".repeat(500) + " ")
                .summaries(withSummaries ? longest : null, withSummaries ? longest : null);
        OssIssueVerdict verdict = IssueGradingOutputValidator.validate(output.build());

        assertThatCode(() -> OssIssueGrade.create(issue(), verdict.difficulty(),
                verdict.problem(), verdict.reproduction(), verdict.cause(), verdict.fixDirection(),
                verdict.relatedPr(), verdict.exclusion(),
                verdict.reasonKo(), verdict.reasonEn(), verdict.summaryKo(), verdict.summaryEn(),
                "v1", "claude-haiku-4-5", SOURCE_HASH)).doesNotThrowAnyException();
    }

    private static Stream<Arguments> acceptedShapes() {
        return Arrays.stream(Exclusion.values())
                .flatMap(exclusion -> Arrays.stream(Level.values())
                        .flatMap(level -> Stream.of(true, false)
                                .filter(withSummaries -> withSummaries || exclusion != Exclusion.NONE)
                                .map(withSummaries -> Arguments.of(exclusion, level, withSummaries))));
    }

    private static List<Tuple> violationsOf(OutputBuilder output) {
        try {
            IssueGradingOutputValidator.validate(output.build());
        } catch (InvalidIssueGradingOutputException e) {
            return e.violations().stream().map(violation -> tuple(violation.field(), violation.kind())).toList();
        }
        throw new AssertionError("거절되어야 하는 출력이 통과했다");
    }

    private static OssIssueEvidence evidenceOf(OssIssueVerdict verdict, String field) {
        Function<OssIssueVerdict, OssIssueEvidence> accessor = switch (field) {
            case "cause" -> OssIssueVerdict::cause;
            case "fixDirection" -> OssIssueVerdict::fixDirection;
            case "problem" -> OssIssueVerdict::problem;
            case "reproduction" -> OssIssueVerdict::reproduction;
            default -> throw new IllegalArgumentException(field);
        };
        return accessor.apply(verdict);
    }

    private static OssIssue issue() {
        return OssIssue.create(OssRepo.create(6296790L, "spring-projects/spring-boot", null, "Java", 80_000),
                5_611_425_470L, 51_878, "Retry interval is ignored", "abc", LocalDateTime.of(2026, 9, 28, 17, 11, 25));
    }

    private static OutputBuilder output() {
        return new OutputBuilder();
    }

    private static final class OutputBuilder {

        private Evidence cause = Evidence.ABSENT;
        private Evidence fixDirection = Evidence.PARTIAL;
        private Evidence problem = Evidence.PRESENT;
        private Boolean relatedPr = true;
        private Evidence reproduction = Evidence.PRESENT;
        private Exclusion exclusion = Exclusion.NONE;
        private Level level = Level.MEDIUM;
        private String reasonEn = "Steps are given but the cause is missing.";
        private String reasonKo = "재현 절차는 있지만 원인이 없다.";
        private Optional<String> summaryEn = Optional.of("Workers linger because of the shutdown hook order.");
        private Optional<String> summaryKo = Optional.of("종료 훅 순서 때문에 워커가 남는다.");

        OutputBuilder evidence(Evidence cause, Evidence fixDirection, Evidence problem, Evidence reproduction) {
            this.cause = cause;
            this.fixDirection = fixDirection;
            this.problem = problem;
            this.reproduction = reproduction;
            return this;
        }

        OutputBuilder evidence(String field, Evidence value) {
            switch (field) {
                case "evidenceCause" -> cause = value;
                case "evidenceFixDirection" -> fixDirection = value;
                case "evidenceProblem" -> problem = value;
                case "evidenceReproduction" -> reproduction = value;
                default -> throw new IllegalArgumentException(field);
            }
            return this;
        }

        OutputBuilder relatedPr(Boolean relatedPr) {
            this.relatedPr = relatedPr;
            return this;
        }

        OutputBuilder exclusion(Exclusion exclusion) {
            this.exclusion = exclusion;
            return this;
        }

        OutputBuilder level(Level level) {
            this.level = level;
            return this;
        }

        OutputBuilder text(String field, String value) {
            switch (field) {
                case "reasonEn" -> reasonEn = value;
                case "reasonKo" -> reasonKo = value;
                case "summaryEn" -> summaryEn = Optional.ofNullable(value);
                case "summaryKo" -> summaryKo = Optional.ofNullable(value);
                default -> throw new IllegalArgumentException(field);
            }
            return this;
        }

        OutputBuilder summaries(String summaryEn, String summaryKo) {
            this.summaryEn = Optional.ofNullable(summaryEn);
            this.summaryKo = Optional.ofNullable(summaryKo);
            return this;
        }

        OutputBuilder without(String field) {
            switch (field) {
                case "evidenceCause", "evidenceFixDirection", "evidenceProblem", "evidenceReproduction" ->
                        evidence(field, null);
                case "evidenceRelatedPr" -> relatedPr = null;
                case "exclusion" -> exclusion = null;
                case "level" -> level = null;
                case "reasonEn", "reasonKo" -> text(field, null);
                default -> throw new IllegalArgumentException(field);
            }
            return this;
        }

        IssueGradingOutput build() {
            return new IssueGradingOutput(cause, fixDirection, problem, relatedPr, reproduction, exclusion, level,
                    reasonEn, reasonKo, summaryEn, summaryKo);
        }
    }
}
