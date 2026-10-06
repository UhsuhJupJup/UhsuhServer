package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import java.util.List;
import java.util.stream.Collectors;

class InvalidIssueGradingOutputException extends RuntimeException {

    private final List<Violation> violations;

    InvalidIssueGradingOutputException(List<Violation> violations) {
        super("판정 출력 거절: " + violations.stream().map(Violation::toString).collect(Collectors.joining(", ")));
        this.violations = List.copyOf(violations);
    }

    List<Violation> violations() {
        return violations;
    }

    record Violation(String field, Kind kind, String detail) {

        enum Kind {
            MISSING,
            BLANK,
            TOO_LONG,
            UNPAIRED,
            URL_OR_EMAIL
        }

        static Violation missing(String field) {
            return new Violation(field, Kind.MISSING, "");
        }

        static Violation blank(String field) {
            return new Violation(field, Kind.BLANK, "");
        }

        static Violation tooLong(String field, int codePoints, int maxCodePoints) {
            return new Violation(field, Kind.TOO_LONG, codePoints + "/" + maxCodePoints);
        }

        static Violation unpaired(String field) {
            return new Violation(field, Kind.UNPAIRED, "");
        }

        static Violation urlOrEmail(String field) {
            return new Violation(field, Kind.URL_OR_EMAIL, "");
        }

        @Override
        public String toString() {
            return detail.isEmpty() ? field + " " + kind : field + " " + kind + " " + detail;
        }
    }
}
