package uhsuhjupjup.backend.oss.contributor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.contributor.application.dto.OssContributorSettingResult;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;
import uhsuhjupjup.backend.oss.contributor.infra.OssContributorSettingRepository;

import java.sql.SQLException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssContributorSettingService {

    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private final OssContributorSettingRepository ossContributorSettingRepository;
    private final OssContributorSettingSaver ossContributorSettingSaver;

    @Transactional(readOnly = true)
    public OssContributorSettingResult getSetting(Member member) {
        OssContributorSetting setting = ossContributorSettingRepository.findById(member.getId())
                .orElseGet(() -> OssContributorSetting.defaults(member));
        return OssContributorSettingResult.of(setting, member.getEmail());
    }

    public OssContributorSettingResult replaceSetting(Member member, OssContributorLanguage language,
                                                      boolean emailEnabled, boolean pushEnabled) {
        OssContributorSetting saved = save(member.getId(), language, emailEnabled, pushEnabled);
        return OssContributorSettingResult.of(saved, member.getEmail());
    }

    private OssContributorSetting save(Long memberId, OssContributorLanguage language, boolean emailEnabled,
                                       boolean pushEnabled) {
        try {
            return ossContributorSettingSaver.save(memberId, language, emailEnabled, pushEnabled);
        } catch (DataIntegrityViolationException firstFailure) {
            if (!isDuplicateKey(firstFailure)) {
                throw firstFailure;
            }
            log.info("회원 {}의 오픈소스 알림 설정 저장이 다른 저장과 겹쳐 한 번 더 저장함", memberId);
            return saveAgain(memberId, language, emailEnabled, pushEnabled, firstFailure);
        }
    }

    private OssContributorSetting saveAgain(Long memberId, OssContributorLanguage language, boolean emailEnabled,
                                            boolean pushEnabled, RuntimeException firstFailure) {
        try {
            return ossContributorSettingSaver.save(memberId, language, emailEnabled, pushEnabled);
        } catch (RuntimeException e) {
            e.addSuppressed(firstFailure);
            throw e;
        }
    }

    private static boolean isDuplicateKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                return true;
            }
        }
        return false;
    }
}
