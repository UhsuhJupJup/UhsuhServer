package uhsuhjupjup.backend.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter("app1");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void 성공한_요청은_로그를_남기지_않는다() throws Exception {
        MockHttpServletResponse response = call("GET", "/api/articles", 200);

        assertThat(appender.list).isEmpty();
        assertThat(response.getHeader("X-Instance")).isEqualTo("app1");
        assertThat(response.getHeader("X-Request-Id")).hasSize(16);
    }

    @Test
    void 클라이언트_오류는_info_로_남긴다() throws Exception {
        call("POST", "/api/subscriptions", 409);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage()).startsWith("POST /api/subscriptions 409");
    }

    @Test
    void 서버_오류는_error_로_남긴다() throws Exception {
        call("GET", "/api/articles", 500);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void 헬스체크는_오류여도_남기지_않는다() throws Exception {
        call("GET", "/actuator/health", 503);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void 전달받은_요청ID를_그대로_이어받는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        request.addHeader("X-Request-Id", "trace-from-client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("trace-from-client");
    }

    @Test
    void 위조된_요청ID는_버리고_새로_발급한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        request.addHeader("X-Request-Id", "bad id\nInjected: line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Request-Id")).hasSize(16);
    }

    private MockHttpServletResponse call(String method, String uri, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }
}
