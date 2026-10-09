package uhsuhjupjup.backend.oss.contributor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;
import uhsuhjupjup.backend.oss.contributor.infra.OssContributorSettingRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OssContributorSettingSaver {

    private final OssContributorSettingRepository ossContributorSettingRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public OssContributorSetting save(Long memberId, OssContributorLanguage language, boolean emailEnabled,
                                      boolean pushEnabled) {
        Optional<OssContributorSetting> stored = ossContributorSettingRepository.findById(memberId);
        if (stored.isEmpty()) {
            return ossContributorSettingRepository.saveAndFlush(OssContributorSetting.create(
                    memberRepository.getReferenceById(memberId), language, emailEnabled, pushEnabled));
        }
        OssContributorSetting setting = stored.get();
        setting.update(language, emailEnabled, pushEnabled);
        return setting;
    }
}
