package uhsuhjupjup.backend.emailsubscription.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.config.JpaAuditingConfig;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.infra.KeywordRepository;
import uhsuhjupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class EmailSubscriptionRepositoryTest extends MySqlTestSupport {

    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;
    @Autowired
    private EmailSubscriptionRepository emailSubscriptionRepository;
    @Autowired
    private KeywordRepository keywordRepository;

    @Test
    void 구독자를_저장하면_토큰과_감사시각이_채워진다() {
        EmailSubscriber saved = emailSubscriberRepository.save(EmailSubscriber.create("a@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUnsubscribeToken()).hasSize(36);
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(emailSubscriberRepository.findByEmail("a@example.com")).isPresent();
        assertThat(emailSubscriberRepository.existsByEmail("a@example.com")).isTrue();
    }

    @Test
    void verify는_최초_시각만_유지한다() {
        EmailSubscriber s = emailSubscriberRepository.save(EmailSubscriber.create("b@example.com"));

        s.verify(LocalDateTime.of(2026, 8, 21, 10, 0));
        s.verify(LocalDateTime.of(2026, 8, 22, 10, 0));

        assertThat(s.getVerifiedAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 0));
        assertThat(s.isVerified()).isTrue();
    }

    @Test
    void 구독을_저장하고_구독자로_조회한다() {
        EmailSubscriber s = emailSubscriberRepository.save(EmailSubscriber.create("c@example.com"));
        Keyword redis = keywordRepository.save(Keyword.create("Redis"));
        emailSubscriptionRepository.save(EmailSubscription.of(s, redis));

        List<EmailSubscription> subs = emailSubscriptionRepository.findByEmailSubscriberId(s.getId());

        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).getKeyword().getName()).isEqualTo("Redis");
    }

    @Test
    void 이메일은_유니크라_중복_저장이_거부된다() {
        emailSubscriberRepository.saveAndFlush(EmailSubscriber.create("dup@example.com"));

        assertThatThrownBy(() ->
                emailSubscriberRepository.saveAndFlush(EmailSubscriber.create("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
