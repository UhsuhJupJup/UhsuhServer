package uhsuhjupjup.backend.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@MySqlDataJpaTest
class GlobalExceptionHandlerMySqlTest {

    private static final String EMAIL = "race@example.com";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final FailingController controller = new FailingController();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void JPA_저장이_MySQL_유일_키에_걸리면_409() throws Exception {
        memberRepository.saveAndFlush(Member.create("google", "uid-1", EMAIL));
        RuntimeException failure = catchRuntimeException(
                () -> memberRepository.saveAndFlush(Member.create("google", "uid-2", EMAIL)));

        assertThat(failure).hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
        respondTo(failure)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void JdbcTemplate_저장이_MySQL_유일_키에_걸리면_DuplicateKeyException이고_409() throws Exception {
        insertMember("uid-1", EMAIL);
        RuntimeException failure = catchRuntimeException(() -> insertMember("uid-2", EMAIL));

        assertThat(failure).isInstanceOf(DuplicateKeyException.class);
        respondTo(failure)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void JPA_저장이_MySQL_NOT_NULL에_걸리면_500() throws Exception {
        RuntimeException failure = catchRuntimeException(
                () -> memberRepository.saveAndFlush(Member.create("google", "uid-1", null)));

        assertThat(failure).hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
        respondTo(failure)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    private void insertMember(String providerUid, String email) {
        jdbcTemplate.update(
                "INSERT INTO member (provider, provider_uid, email, unsubscribe_token) VALUES (?, ?, ?, ?)",
                "google", providerUid, email, UUID.randomUUID().toString());
    }

    private ResultActions respondTo(RuntimeException failure) throws Exception {
        controller.failWith(failure);
        return mockMvc.perform(put("/stub/resources"));
    }

    @RestController
    static class FailingController {

        private RuntimeException failure;

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @PutMapping("/stub/resources")
        void save() {
            throw failure;
        }
    }
}
