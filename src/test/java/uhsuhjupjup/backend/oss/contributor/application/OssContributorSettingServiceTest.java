package uhsuhjupjup.backend.oss.contributor.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.contributor.application.dto.OssContributorSettingResult;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorLanguage;
import uhsuhjupjup.backend.oss.contributor.domain.OssContributorSetting;
import uhsuhjupjup.backend.oss.contributor.infra.OssContributorSettingRepository;
import uhsuhjupjup.backend.support.MemberFixture;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OssContributorSettingServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String LOGIN_EMAIL = "user@example.com";
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;
    private static final int MYSQL_NO_REFERENCED_ROW = 1452;
    private static final String SAVED_ONCE_MORE = "회원 1의 오픈소스 알림 설정 저장이 다른 저장과 겹쳐 한 번 더 저장함";

    @Mock
    private OssContributorSettingRepository ossContributorSettingRepository;

    @Mock
    private OssContributorSettingSaver ossContributorSettingSaver;

    @InjectMocks
    private OssContributorSettingService ossContributorSettingService;

    private final Member member = MemberFixture.member(MEMBER_ID, LOGIN_EMAIL);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(OssContributorSettingService.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logs);
    }

    @Test
    void getSetting_neverSaved_returnsDefaultsWithLoginEmailWithoutSaving() {
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        OssContributorSettingResult result = ossContributorSettingService.getSetting(member);

        assertThat(result).isEqualTo(
                new OssContributorSettingResult(OssContributorLanguage.KO, true, false, LOGIN_EMAIL));
        then(ossContributorSettingRepository).should(never()).save(any());
        then(ossContributorSettingRepository).should(never()).saveAndFlush(any());
        then(ossContributorSettingSaver).shouldHaveNoInteractions();
    }

    @Test
    void getSetting_saved_returnsStoredValuesWithLoginEmail() {
        given(ossContributorSettingRepository.findById(MEMBER_ID)).willReturn(
                Optional.of(OssContributorSetting.create(member, OssContributorLanguage.EN, false, true)));

        OssContributorSettingResult result = ossContributorSettingService.getSetting(member);

        assertThat(result).isEqualTo(
                new OssContributorSettingResult(OssContributorLanguage.EN, false, true, LOGIN_EMAIL));
    }

    @Test
    void replaceSetting_returnsWhatWasSavedWithLoginEmail() {
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willReturn(OssContributorSetting.create(member, OssContributorLanguage.EN, false, true));

        OssContributorSettingResult result = ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true);

        assertThat(result).isEqualTo(
                new OssContributorSettingResult(OssContributorLanguage.EN, false, true, LOGIN_EMAIL));
        then(ossContributorSettingSaver).should(times(1)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void replaceSetting_bothChannelsOff_isSavedAndReturnedAsIs() {
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.KO, false, false))
                .willReturn(OssContributorSetting.create(member, OssContributorLanguage.KO, false, false));

        OssContributorSettingResult result = ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.KO, false, false);

        assertThat(result).isEqualTo(
                new OssContributorSettingResult(OssContributorLanguage.KO, false, false, LOGIN_EMAIL));
    }

    @Test
    void replaceSetting_firstSaveHitsDuplicateKeyOfAnotherFirstSave_savesOnceMore() {
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willThrow(duplicateKey())
                .willReturn(OssContributorSetting.create(member, OssContributorLanguage.EN, false, true));

        OssContributorSettingResult result = ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true);

        assertThat(result).isEqualTo(
                new OssContributorSettingResult(OssContributorLanguage.EN, false, true, LOGIN_EMAIL));
        then(ossContributorSettingSaver).should(times(2)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(SAVED_ONCE_MORE);
    }

    @Test
    void replaceSetting_failsAgainAfterSavingOnceMore_throwsSecondFailureWithFirstSuppressedWithoutThirdTry() {
        DataIntegrityViolationException first = duplicateKey();
        DataIntegrityViolationException again = memberGone();
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willThrow(first)
                .willThrow(again);

        assertThatThrownBy(() -> ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true))
                .isSameAs(again)
                .hasSuppressedException(first);

        then(ossContributorSettingSaver).should(times(2)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
    }

    @Test
    void replaceSetting_foreignKeyViolation_throwsItWithoutSavingAgainOrLogging() {
        DataIntegrityViolationException memberGone = memberGone();
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willThrow(memberGone);

        assertThatThrownBy(() -> ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true))
                .isSameAs(memberGone);

        then(ossContributorSettingSaver).should(times(1)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void replaceSetting_integrityViolationWithoutSqlCause_throwsItWithoutSavingAgain() {
        DataIntegrityViolationException withoutCause = new DataIntegrityViolationException("not-null property");
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willThrow(withoutCause);

        assertThatThrownBy(() -> ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true))
                .isSameAs(withoutCause);

        then(ossContributorSettingSaver).should(times(1)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void replaceSetting_failsForAnotherReason_throwsItWithoutSavingAgain() {
        QueryTimeoutException timeout = new QueryTimeoutException("Statement cancelled due to timeout");
        given(ossContributorSettingSaver.save(MEMBER_ID, OssContributorLanguage.EN, false, true))
                .willThrow(timeout);

        assertThatThrownBy(() -> ossContributorSettingService.replaceSetting(
                member, OssContributorLanguage.EN, false, true))
                .isSameAs(timeout);

        then(ossContributorSettingSaver).should(times(1)).save(MEMBER_ID, OssContributorLanguage.EN, false, true);
    }

    private static DataIntegrityViolationException duplicateKey() {
        return rejectedByMySql(MYSQL_DUPLICATE_ENTRY, "PRIMARY",
                "Duplicate entry '1' for key 'oss_contributor_setting.PRIMARY'");
    }

    private static DataIntegrityViolationException memberGone() {
        return rejectedByMySql(MYSQL_NO_REFERENCED_ROW, "fk_oss_contributor_setting_member",
                "Cannot add or update a child row: a foreign key constraint fails");
    }

    private static DataIntegrityViolationException rejectedByMySql(int errorCode, String constraint, String reason) {
        SQLIntegrityConstraintViolationException rejected =
                new SQLIntegrityConstraintViolationException(reason, "23000", errorCode);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", rejected, constraint));
    }
}
