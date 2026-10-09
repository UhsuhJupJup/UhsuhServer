package uhsuhjupjup.backend.oss.contributor.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.support.MemberFixture;

import static org.assertj.core.api.Assertions.assertThat;

class OssContributorSettingTest {

    private final Member member = MemberFixture.member(1L, "user@example.com");

    @Test
    void create_keepsMemberLanguageAndBothChannels() {
        OssContributorSetting setting = OssContributorSetting.create(member, OssContributorLanguage.EN, false, true);

        assertThat(setting.getMember()).isSameAs(member);
        assertThat(setting.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.isPushEnabled()).isTrue();
    }

    @Test
    void defaults_isKoreanWithEmailOnAndPushOff() {
        OssContributorSetting setting = OssContributorSetting.defaults(member);

        assertThat(setting.getMember()).isSameAs(member);
        assertThat(setting.getLanguage()).isEqualTo(OssContributorLanguage.KO);
        assertThat(setting.isEmailEnabled()).isTrue();
        assertThat(setting.isPushEnabled()).isFalse();
    }

    @Test
    void update_replacesLanguageAndBothChannels() {
        OssContributorSetting setting = OssContributorSetting.create(member, OssContributorLanguage.KO, true, false);

        setting.update(OssContributorLanguage.EN, false, true);

        assertThat(setting.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.isPushEnabled()).isTrue();
        assertThat(setting.getMember()).isSameAs(member);
    }

    @Test
    void update_bothChannelsOff_isKept() {
        OssContributorSetting setting = OssContributorSetting.defaults(member);

        setting.update(OssContributorLanguage.KO, false, false);

        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.isPushEnabled()).isFalse();
    }
}
