package uhsuhjupjup.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String INSTANCE_HEADER = "X-Instance";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final Pattern ACCEPTABLE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String HEALTH_PATH_PREFIX = "/actuator";
    private static final int GENERATED_LENGTH = 16;
    private static final int CLIENT_ERROR = 400;
    private static final int SERVER_ERROR = 500;

    private final String instanceName;

    public RequestLoggingFilter(@Value("${app.instance-name:local}") String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(INSTANCE_HEADER, instanceName);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logCompletion(request, response.getStatus(), elapsedMillis(startedAt));
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void logCompletion(HttpServletRequest request, int status, long tookMs) {
        if (request.getRequestURI().startsWith(HEALTH_PATH_PREFIX)) {
            return;
        }
        if (status >= SERVER_ERROR) {
            log.error("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, tookMs);
            return;
        }
        if (status >= CLIENT_ERROR) {
            log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, tookMs);
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String resolveRequestId(String header) {
        if (header != null && ACCEPTABLE_REQUEST_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, GENERATED_LENGTH);
    }
}
