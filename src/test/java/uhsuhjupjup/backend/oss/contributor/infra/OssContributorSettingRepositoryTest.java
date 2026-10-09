package uhsuhjupjup.backend.oss.contributor.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssContributorSettingRepositoryTest {

    @Autowired
    private OssContributorSettingRepository ossContributorSettingRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_storesSettingUnderMemberIdWithLanguageAsEnumName() {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        ossContributorSettingRepository.saveAndFlush(
                OssContributorSetting.create(member, OssContributorLanguage.EN, false, true));
        entityManager.clear();

        OssContributorSetting loaded = ossContributorSettingRepository.findById(member.getId()).orElseThrow();

        assertThat(loaded.getMemberId()).isEqualTo(member.getId());
        assertThat(loaded.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(loaded.isEmailEnabled()).isFalse();
        assertThat(loaded.isPushEnabled()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(storedLanguage(member.getId())).isEqualTo("EN");
    }

    @Test
    void findById_memberWithoutSetting_returnsEmpty() {
        Member member = memberRepository.saveAndFlush(Member.create("google", "uid-1", "user@example.com"));
        entityManager.clear();

        assertThat(ossContributorSettingRepository.findById(member.getId())).isEmpty();
    }

    @Test
    void findById_returnsOnlyThatMembersSetting() {
        Member korean = memberRepository.save(Member.create("google", "uid-1", "korean@example.com"));
        Member english = memberRepository.save(Member.create("google", "uid-2", "english@example.com"));
        ossContributorSettingRepository.save(
                OssContributorSetting.create(korean, OssContributorLanguage.KO, true, false));
        ossContributorSettingRepository.save(
                OssContributorSetting.create(english, OssContributorLanguage.EN, false, true));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossContributorSettingRepository.findById(korean.getId()))
                .map(OssContributorSetting::getLanguage)
                .hasValue(OssContributorLanguage.KO);
        assertThat(ossContributorSettingRepository.findById(english.getId()))
                .map(OssContributorSetting::getLanguage)
                .hasValue(OssContributorLanguage.EN);
    }

    @Test
    void findById_loadsSettingWithoutLoadingMember() {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        ossContributorSettingRepository.saveAndFlush(OssContributorSetting.defaults(member));
        entityManager.clear();

        OssContributorSetting loaded = ossContributorSettingRepository.findById(member.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getMember())).isFalse();
    }

    @Test
    void update_isWrittenByDirtyCheckingWithoutSave() {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        ossContributorSettingRepository.saveAndFlush(OssContributorSetting.defaults(member));
        entityManager.clear();

        ossContributorSettingRepository.findById(member.getId()).orElseThrow()
                .update(OssContributorLanguage.EN, false, false);
        entityManager.flush();
        entityManager.clear();

        OssContributorSetting reloaded = ossContributorSettingRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(reloaded.isEmailEnabled()).isFalse();
        assertThat(reloaded.isPushEnabled()).isFalse();
    }

    @Test
    void update_withSameValues_leavesRowUntouched() {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        ossContributorSettingRepository.saveAndFlush(
                OssContributorSetting.create(member, OssContributorLanguage.EN, false, true));
        LocalDateTime longAgo = LocalDateTime.of(2026, 1, 1, 0, 0);
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE oss_contributor_setting SET updated_at = ?1 WHERE member_id = ?2")
                .setParameter(1, longAgo)
                .setParameter(2, member.getId())
                .executeUpdate();
        entityManager.clear();

        ossContributorSettingRepository.findById(member.getId()).orElseThrow()
                .update(OssContributorLanguage.EN, false, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(ossContributorSettingRepository.findById(member.getId()).orElseThrow().getUpdatedAt())
                .isEqualTo(longAgo);
    }

    @Test
    void secondSettingForSameMember_violatesPrimaryKey() {
        Member member = memberRepository.save(Member.create("google", "uid-1", "user@example.com"));
        ossContributorSettingRepository.saveAndFlush(OssContributorSetting.defaults(member));
        entityManager.clear();
        Member reference = memberRepository.getReferenceById(member.getId());

        assertThatThrownBy(() -> ossContributorSettingRepository.saveAndFlush(
                OssContributorSetting.create(reference, OssContributorLanguage.EN, false, true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("PRIMARY");
    }

    @Test
    void settingForMissingMember_violatesForeignKey() {
        Member missing = memberRepository.getReferenceById(999_999L);

        assertThatThrownBy(() -> ossContributorSettingRepository.saveAndFlush(
                OssContributorSetting.defaults(missing)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_oss_contributor_setting_member");
    }

    private String storedLanguage(Long memberId) {
        return (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT language FROM oss_contributor_setting WHERE member_id = ?1")
                .setParameter(1, memberId)
                .getSingleResult();
    }
}
