package uhseojupjup.backend.member.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import uhseojupjup.backend.config.JpaAuditingConfig;
import uhseojupjup.backend.member.domain.Member;
import uhseojupjup.backend.support.MySqlTestSupport;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MemberRepositoryTest extends MySqlTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByEmail_returnsSavedMember() {
        memberRepository.saveAndFlush(Member.create("github", "uid-1", "octocat@github.com"));

        Optional<Member> found = memberRepository.findByEmail("octocat@github.com");

        assertThat(found).isPresent();
        assertThat(found.get().getProvider()).isEqualTo("github");
    }

    @Test
    void findByUnsubscribeToken_returnsSavedMember() {
        Member saved = memberRepository.saveAndFlush(Member.create("github", "uid-1", "octocat@github.com"));

        Optional<Member> found = memberRepository.findByUnsubscribeToken(saved.getUnsubscribeToken());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void duplicateEmail_violatesUniqueConstraint() {
        memberRepository.saveAndFlush(Member.create("github", "uid-1", "octocat@github.com"));
        Member duplicate = Member.create("google", "uid-2", "octocat@github.com");

        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dirtyChecking_persistsConsentWithoutSave() {
        Member saved = memberRepository.saveAndFlush(Member.create("github", "uid-1", "octocat@github.com"));
        Long id = saved.getId();
        entityManager.clear();

        Member loaded = memberRepository.findById(id).orElseThrow();
        loaded.agreeConsent(LocalDateTime.of(2026, 6, 25, 12, 0));
        entityManager.flush();
        entityManager.clear();

        Member reloaded = memberRepository.findById(id).orElseThrow();
        assertThat(reloaded.getConsentAt()).isEqualTo(LocalDateTime.of(2026, 6, 25, 12, 0));
    }
}
