package uhsuhjupjup.backend.oss.pipeline.grading.infra;

import java.math.BigDecimal;

final class TemperatureSetting {

    private TemperatureSetting() {
    }

    static Double parse(String value, BigDecimal max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal temperature = numberOf(value, max);
        if (temperature.compareTo(BigDecimal.ZERO) < 0 || temperature.compareTo(max) > 0) {
            throw invalid(value, max, null);
        }
        return temperature.doubleValue();
    }

    private static BigDecimal numberOf(String value, BigDecimal max) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            throw invalid(value, max, e);
        }
    }

    private static IllegalArgumentException invalid(String value, BigDecimal max, NumberFormatException cause) {
        return new IllegalArgumentException("이슈 판정 temperature는 비우거나 0 이상 " + max.toPlainString()
                + " 이하의 숫자여야 합니다: " + value, cause);
    }
}
