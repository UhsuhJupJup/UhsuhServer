package uhsuhjupjup.backend.member.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void create_generatesUnsubscribeToken_andNotConsented() {
        Member member = Member.create("github", "uid-1", "octocat@github.com");

        assertThat(member.getProvider()).isEqualTo("github");
        assertThat(member.getProviderUid()).isEqualTo("uid-1");
        assertThat(member.getEmail()).isEqualTo("octocat@github.com");
        assertThat(member.getUnsubscribeToken()).hasSize(36);
        assertThat(member.getConsentAt()).isNull();
        assertThat(member.hasConsented()).isFalse();
    }

    @Test
    void agreeConsent_setsConsentAt() {
        Member member = Member.create("github", "uid-1", "octocat@github.com");
        LocalDateTime at = LocalDateTime.of(2026, 6, 24, 9, 10);

        member.agreeConsent(at);

        assertThat(member.getConsentAt()).isEqualTo(at);
        assertThat(member.hasConsented()).isTrue();
    }

    @Test
    void agreeConsent_isIdempotent_keepsFirstTime() {
        Member member = Member.create("github", "uid-1", "octocat@github.com");
        LocalDateTime first = LocalDateTime.of(2026, 6, 24, 9, 10);
        LocalDateTime second = LocalDateTime.of(2026, 6, 25, 12, 0);

        member.agreeConsent(first);
        member.agreeConsent(second);

        assertThat(member.getConsentAt()).isEqualTo(first);
    }
}
