package uhsuhjupjup.backend.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EcsLogFormatContractTest {

    @Test
    void ecs_로그가_alloy_파이프라인이_기대하는_모양으로_출력된다() throws Exception {
        JsonNode line = encodeWarnLine("abc123");

        assertThat(line.at("/log/level").asText()).isEqualTo("WARN");
        assertThat(line.get("requestId").asText()).isEqualTo("abc123");
        assertThat(line.get("message").asText()).isEqualTo("hello");
    }

    private JsonNode encodeWarnLine(String requestId) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.putObject(Environment.class.getName(), new MockEnvironment());

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat("ecs");
        encoder.setContext(context);
        encoder.start();

        MDC.put("requestId", requestId);
        try {
            ILoggingEvent event = new LoggingEvent(
                    "probe", context.getLogger("probe"), Level.WARN, "hello", null, null);
            byte[] encoded = encoder.encode(event);
            return new ObjectMapper().readTree(new String(encoded, StandardCharsets.UTF_8));
        } finally {
            MDC.clear();
            encoder.stop();
        }
    }
}
