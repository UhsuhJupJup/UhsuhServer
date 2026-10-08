package uhsuhjupjup.backend.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.constraints.Min;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String DUPLICATE_EMAIL = "dup@example.com";

    private final StubController controller = new StubController();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void 숫자_자리에_문자가_오면_400과_파라미터_이름을_준다() throws Exception {
        mockMvc.perform(get("/stub/resources/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/stub/resources/abc"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void 숫자가_아닌_타입의_형식이_틀려도_400이고_입력값은_응답에_싣지_않는다() throws Exception {
        mockMvc.perform(get("/stub/logs").param("date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("not-a-date"))));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void 파라미터가_허용_범위를_벗어나면_400과_파라미터_이름과_이유를_준다() throws Exception {
        mockMvc.perform(get("/stub/pages").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/stub/pages"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("1 이상이어야 합니다."));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void 이름을_지정한_파라미터는_범위_위반도_형식_오류와_같은_요청_이름을_준다() throws Exception {
        mockMvc.perform(get("/stub/named-pages").param("page-size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page-size"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("1 이상이어야 합니다."));
        mockMvc.perform(get("/stub/named-pages").param("page-size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page-size"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("숫자여야 합니다."));
        mockMvc.perform(get("/stub/levels/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("level-id"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("1 이상이어야 합니다."));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void 반환값_검증_실패는_서버_오류라_500이고_에러_로그를_남긴다() throws Exception {
        mockMvc.perform(get("/stub/count"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/stub/count"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(HandlerMethodValidationException.class.getName());
        });
    }

    @Test
    void 필수_파라미터가_없으면_400과_파라미터_이름을_준다() throws Exception {
        mockMvc.perform(get("/stub/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/stub/confirm"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("필수 값입니다."));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void JPA를_거친_유일_키_위반은_409이고_경로만_담은_경고를_남긴다() throws Exception {
        controller.failWith(jpaViolation(ConstraintKind.UNIQUE, mysqlError(1062,
                "Duplicate entry '" + DUPLICATE_EMAIL + "' for key 'member.uk_member_email'")));

        mockMvc.perform(put("/stub/resources"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 존재하는 리소스입니다."))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/stub/resources"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(content().string(not(containsString(DUPLICATE_EMAIL))));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("/stub/resources").doesNotContain(DUPLICATE_EMAIL);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void DuplicateKeyException으로_바뀐_유일_키_위반도_409() throws Exception {
        controller.failWith(new DuplicateKeyException("PreparedStatementCallback; Duplicate entry",
                mysqlError(1062, "Duplicate entry '" + DUPLICATE_EMAIL + "' for key 'member.uk_member_email'")));

        mockMvc.perform(put("/stub/resources"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void 외래_키_위반은_500이고_에러_로그를_남긴다() throws Exception {
        controller.failWith(jpaViolation(ConstraintKind.OTHER, mysqlError(1452,
                "Cannot add or update a child row: a foreign key constraint fails")));

        mockMvc.perform(put("/stub/resources"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void NOT_NULL_위반은_500() throws Exception {
        controller.failWith(jpaViolation(ConstraintKind.OTHER, mysqlError(1048, "Column 'email' cannot be null")));

        mockMvc.perform(put("/stub/resources"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void MySQL_1062가_없는_DuplicateKeyException은_500() throws Exception {
        controller.failWith(new DuplicateKeyException("different object with the same identifier value",
                new NonUniqueObjectException(1L, "Member")));

        mockMvc.perform(put("/stub/resources"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    private static SQLIntegrityConstraintViolationException mysqlError(int errorCode, String message) {
        return new SQLIntegrityConstraintViolationException(message, "23000", errorCode);
    }

    private static DataIntegrityViolationException jpaViolation(ConstraintKind kind,
                                                                SQLIntegrityConstraintViolationException mysqlError) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", mysqlError, "insert into member",
                        kind, null));
    }

    @RestController
    static class StubController {

        private RuntimeException failure;

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @GetMapping("/stub/resources/{id}")
        void find(@PathVariable Long id) {
        }

        @GetMapping("/stub/logs")
        void logs(@RequestParam LocalDate date) {
        }

        @GetMapping("/stub/confirm")
        void confirm(@RequestParam String token) {
        }

        @GetMapping("/stub/pages")
        public void pages(@RequestParam @Min(value = 1, message = "1 이상이어야 합니다.") int size) {
        }

        @GetMapping("/stub/named-pages")
        public void namedPages(@RequestParam("page-size") @Min(value = 1, message = "1 이상이어야 합니다.")
                               int pageSize) {
        }

        @GetMapping("/stub/levels/{level-id}")
        public void level(@PathVariable("level-id") @Min(value = 1, message = "1 이상이어야 합니다.") long levelId) {
        }

        @GetMapping("/stub/count")
        @Min(1)
        public int count() {
            return 0;
        }

        @PutMapping("/stub/resources")
        void save() {
            throw failure;
        }
    }
}
