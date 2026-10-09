package uhsuhjupjup.backend.oss.contributor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;
import uhsuhjupjup.backend.member.domain.Member;

@Entity
@Table(name = "oss_contributor_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OssContributorSetting extends BaseEntity {

    private static final OssContributorLanguage DEFAULT_LANGUAGE = OssContributorLanguage.KO;
    private static final boolean DEFAULT_EMAIL_ENABLED = true;
    private static final boolean DEFAULT_PUSH_ENABLED = false;

    @Id
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private OssContributorLanguage language;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    private OssContributorSetting(Member member, OssContributorLanguage language, boolean emailEnabled,
                                  boolean pushEnabled) {
        this.member = member;
        this.language = language;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }

    public static OssContributorSetting create(Member member, OssContributorLanguage language, boolean emailEnabled,
                                               boolean pushEnabled) {
        return new OssContributorSetting(member, language, emailEnabled, pushEnabled);
    }

    public static OssContributorSetting defaults(Member member) {
        return new OssContributorSetting(member, DEFAULT_LANGUAGE, DEFAULT_EMAIL_ENABLED, DEFAULT_PUSH_ENABLED);
    }

    public void update(OssContributorLanguage language, boolean emailEnabled, boolean pushEnabled) {
        this.language = language;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }
}
