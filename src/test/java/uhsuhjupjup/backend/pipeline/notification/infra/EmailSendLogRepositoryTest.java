package uhsuhjupjup.backend.pipeline.notification.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.pipeline.notification.domain.EmailSendLog;
import uhsuhjupjup.backend.pipeline.notification.domain.RecipientType;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class EmailSendLogRepositoryTest extends MySqlTestSupport {

    @Autowired
    private EmailSendLogRepository emailSendLogRepository;

    @Test
    void 발송로그를_저장하고_최신순으로_조회한다() {
        emailSendLogRepository.save(EmailSendLog.of("a@example.com", RecipientType.MEMBER, 3, "제목 A"));
        emailSendLogRepository.save(EmailSendLog.of("b@example.com", RecipientType.EMAIL_SUBSCRIBER, 1, "제목 B"));

        List<EmailSendLog> logs = emailSendLogRepository.findAllByOrderBySentAtDesc(PageRequest.of(0, 10));

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(EmailSendLog::getEmail).contains("a@example.com", "b@example.com");
        assertThat(logs).extracting(EmailSendLog::getRecipientType)
                .contains(RecipientType.MEMBER, RecipientType.EMAIL_SUBSCRIBER);
        assertThat(logs.get(0).getSentAt()).isNotNull();
    }

    @Test
    void limit으로_개수를_제한한다() {
        for (int i = 0; i < 5; i++) {
            emailSendLogRepository.save(EmailSendLog.of("u" + i + "@example.com", RecipientType.MEMBER, 1, "제목"));
        }

        assertThat(emailSendLogRepository.findAllByOrderBySentAtDesc(PageRequest.of(0, 3))).hasSize(3);
    }
}
