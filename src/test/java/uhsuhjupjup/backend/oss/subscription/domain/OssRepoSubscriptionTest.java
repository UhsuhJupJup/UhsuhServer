package uhsuhjupjup.backend.oss.subscription.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.support.MemberFixture;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.EASY;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.HARD;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.MEDIUM;

class OssRepoSubscriptionTest {

    private final Member member = MemberFixture.member(1L, "user@example.com");
    private final OssRepo repo = OssRepo.create(6296790L, "spring-projects/spring-boot", null, "Java", 80_000);

    @Test
    void create_keepsMemberAndRepoAndStoresDifficultiesAsMask() {
        OssRepoSubscription subscription = OssRepoSubscription.create(member, repo, Set.of(EASY, HARD));

        assertThat(subscription.getMember()).isSameAs(member);
        assertThat(subscription.getRepo()).isSameAs(repo);
        assertThat(subscription.getDifficultyMask()).isEqualTo(5);
        assertThat(subscription.difficulties()).containsExactlyInAnyOrder(EASY, HARD);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void create_noDifficulty_isRejected(Set<OssIssueDifficulty> difficulties) {
        assertThatThrownBy(() -> OssRepoSubscription.create(member, repo, difficulties))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void changeDifficulties_replacesTheMask() {
        OssRepoSubscription subscription = OssRepoSubscription.create(member, repo, Set.of(EASY));

        subscription.changeDifficulties(Set.of(MEDIUM, HARD));

        assertThat(subscription.getDifficultyMask()).isEqualTo(6);
        assertThat(subscription.difficulties()).containsExactlyInAnyOrder(MEDIUM, HARD);
        assertThat(subscription.getMember()).isSameAs(member);
        assertThat(subscription.getRepo()).isSameAs(repo);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void changeDifficulties_noDifficulty_isRejectedAndKeepsTheMask(Set<OssIssueDifficulty> difficulties) {
        OssRepoSubscription subscription = OssRepoSubscription.create(member, repo, Set.of(EASY, MEDIUM));

        assertThatThrownBy(() -> subscription.changeDifficulties(difficulties))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(subscription.getDifficultyMask()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void difficulties_brokenStoredMask_isRejected(int brokenMask) {
        OssRepoSubscription subscription = OssRepoSubscription.create(member, repo, Set.of(EASY));
        ReflectionTestUtils.setField(subscription, "difficultyMask", brokenMask);

        assertThatThrownBy(subscription::difficulties)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}
