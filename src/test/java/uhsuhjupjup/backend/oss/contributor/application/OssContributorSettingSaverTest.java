package uhsuhjupjup.backend.oss.contributor.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;
import uhsuhjupjup.backend.oss.contributor.infra.OssContributorSettingRepository;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OssContributorSettingSaverTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private OssContributorSettingRepository ossContributorSettingRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private OssContributorSettingSaver ossContributorSettingSaver;

    private final Member member = MemberFixture.member(MEMBER_ID, "user@example.com");

    @Test
    void save_memberWithoutSetting_createsOneForMemberReferenceWithGivenValues() {
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
        given(ossContributorSettingRepository.saveAndFlush(any(OssContributorSetting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OssContributorSetting saved = ossContributorSettingSaver.save(
                MEMBER_ID, OssContributorLanguage.EN, false, true);

        ArgumentCaptor<OssContributorSetting> created = ArgumentCaptor.forClass(OssContributorSetting.class);
        then(ossContributorSettingRepository).should().saveAndFlush(created.capture());
        assertThat(saved).isSameAs(created.getValue());
        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(saved.isEmailEnabled()).isFalse();
        assertThat(saved.isPushEnabled()).isTrue();
    }

    @Test
    void save_memberWithSetting_updatesItWithoutCreatingAnother() {
        OssContributorSetting stored = OssContributorSetting.create(member, OssContributorLanguage.KO, true, false);
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(Optional.of(stored));

        OssContributorSetting saved = ossContributorSettingSaver.save(
                MEMBER_ID, OssContributorLanguage.EN, false, true);

        assertThat(saved).isSameAs(stored);
        assertThat(saved.getLanguage()).isEqualTo(OssContributorLanguage.EN);
        assertThat(saved.isEmailEnabled()).isFalse();
        assertThat(saved.isPushEnabled()).isTrue();
        then(ossContributorSettingRepository).should(never()).saveAndFlush(any());
        then(ossContributorSettingRepository).should(never()).save(any());
        then(memberRepository).should(never()).getReferenceById(anyLong());
    }

    @Test
    void save_bothChannelsOffWithoutSetting_createsItAsIs() {
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(member);
        given(ossContributorSettingRepository.saveAndFlush(any(OssContributorSetting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OssContributorSetting saved = ossContributorSettingSaver.save(
                MEMBER_ID, OssContributorLanguage.KO, false, false);

        assertThat(saved.isEmailEnabled()).isFalse();
        assertThat(saved.isPushEnabled()).isFalse();
    }

    @Test
    void save_bothChannelsOffWithSetting_updatesItAsIs() {
        OssContributorSetting stored = OssContributorSetting.defaults(member);
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(Optional.of(stored));

        OssContributorSetting saved = ossContributorSettingSaver.save(
                MEMBER_ID, OssContributorLanguage.KO, false, false);

        assertThat(saved.isEmailEnabled()).isFalse();
        assertThat(saved.isPushEnabled()).isFalse();
    }
}
