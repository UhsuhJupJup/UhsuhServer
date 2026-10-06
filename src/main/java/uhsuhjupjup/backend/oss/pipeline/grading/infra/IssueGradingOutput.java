package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.Optional;

record IssueGradingOutput(
        Evidence evidenceCause,
        Evidence evidenceFixDirection,
        Evidence evidenceProblem,
        Boolean evidenceRelatedPr,
        Evidence evidenceReproduction,
        Exclusion exclusion,
        Level level,
        String reasonEn,
        String reasonKo,
        Optional<String> summaryEn,
        Optional<String> summaryKo) {

    enum Evidence {
        PRESENT,
        PARTIAL,
        ABSENT;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Evidence fromJson(String value) {
            return ignoringCase(values(), value);
        }
    }

    enum Exclusion {
        NONE,
        UNKNOWN_CAUSE,
        QUESTION,
        SPAM,
        DUPLICATE;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Exclusion fromJson(String value) {
            return ignoringCase(values(), value);
        }
    }

    enum Level {
        EASY,
        MEDIUM,
        HARD;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Level fromJson(String value) {
            return ignoringCase(values(), value);
        }
    }

    private static <E extends Enum<E>> E ignoringCase(E[] constants, String value) {
        return Arrays.stream(constants)
                .filter(constant -> constant.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }
}
