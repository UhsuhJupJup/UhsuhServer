package uhsuhjupjup.backend.oss.subscription.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.oss.subscription.domain.OssDifficultyMask;
import uhsuhjupjup.backend.oss.subscription.domain.OssRepoSubscription;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.EASY;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.HARD;
import static uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty.MEDIUM;

@MySqlDataJpaTest
class OssRepoSubscriptionRepositoryTest {

    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    @Autowired
    private OssRepoSubscriptionRepository ossRepoSubscriptionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByMemberIdAndRepoId_returnsSavedSubscriptionWithoutLoadingMemberOrRepo() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepoSubscription saved = ossRepoSubscriptionRepository.saveAndFlush(
                OssRepoSubscription.create(member, repo, Set.of(EASY, HARD)));
        entityManager.clear();

        OssRepoSubscription loaded = ossRepoSubscriptionRepository
                .findByMemberIdAndRepoId(member.getId(), repo.getId())
                .orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getMember())).isFalse();
        assertThat(Hibernate.isInitialized(loaded.getRepo())).isFalse();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.difficulties()).containsExactlyInAnyOrder(EASY, HARD);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByMemberIdAndRepoId_findsOnlyThatMembersSubscriptionToThatRepo() {
        Member alice = memberRepository.save(member("uid-1", "alice@example.com"));
        Member bob = memberRepository.save(member("uid-2", "bob@example.com"));
        OssRepo boot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(alice, boot, Set.of(EASY)));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(alice, react, Set.of(MEDIUM)));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(bob, boot, Set.of(HARD)));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoSubscriptionRepository.findByMemberIdAndRepoId(alice.getId(), boot.getId()))
                .map(OssRepoSubscription::difficulties)
                .hasValue(Set.of(EASY));
        assertThat(ossRepoSubscriptionRepository.findByMemberIdAndRepoId(alice.getId(), react.getId()))
                .map(OssRepoSubscription::difficulties)
                .hasValue(Set.of(MEDIUM));
        assertThat(ossRepoSubscriptionRepository.findByMemberIdAndRepoId(bob.getId(), boot.getId()))
                .map(OssRepoSubscription::difficulties)
                .hasValue(Set.of(HARD));
        assertThat(ossRepoSubscriptionRepository.findByMemberIdAndRepoId(bob.getId(), react.getId())).isEmpty();
    }

    @Test
    void sameMemberAndRepoTwice_violatesUniqueKeyAsDuplicateEntry() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoSubscriptionRepository.saveAndFlush(OssRepoSubscription.create(member, repo, Set.of(EASY)));

        assertThatThrownBy(() -> ossRepoSubscriptionRepository.saveAndFlush(
                OssRepoSubscription.create(member, repo, Set.of(HARD))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_repo_subscription_member_repo")
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MYSQL_DUPLICATE_ENTRY));
    }

    @Test
    void deletingMember_deletesOnlyTheirSubscriptions() {
        Member alice = memberRepository.save(member("uid-1", "alice@example.com"));
        Member bob = memberRepository.save(member("uid-2", "bob@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(alice, repo, Set.of(EASY)));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(bob, repo, Set.of(HARD)));
        entityManager.flush();
        entityManager.clear();

        memberRepository.deleteById(alice.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoSubscriptionRepository.findAll())
                .extracting(subscription -> subscription.getMember().getId())
                .containsExactly(bob.getId());
    }

    @Test
    void deletingRepo_deletesOnlyItsSubscriptions() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo boot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(member, boot, Set.of(EASY)));
        ossRepoSubscriptionRepository.save(OssRepoSubscription.create(member, react, Set.of(MEDIUM)));
        entityManager.flush();
        entityManager.clear();

        ossRepoRepository.deleteById(boot.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoSubscriptionRepository.findAll())
                .extracting(subscription -> subscription.getRepo().getId())
                .containsExactly(react.getId());
    }

    @Test
    void difficultyMask_isStoredAsInteger() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));

        OssRepoSubscription saved = ossRepoSubscriptionRepository.saveAndFlush(
                OssRepoSubscription.create(member, repo, Set.of(EASY, HARD)));

        assertThat(storedMask(saved.getId())).isEqualTo(5);
    }

    @Test
    void changeDifficulties_isWrittenByDirtyCheckingWithoutSave() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepoSubscription saved = ossRepoSubscriptionRepository.saveAndFlush(
                OssRepoSubscription.create(member, repo, Set.of(EASY, HARD)));
        entityManager.clear();

        ossRepoSubscriptionRepository.findByMemberIdAndRepoId(member.getId(), repo.getId())
                .orElseThrow()
                .changeDifficulties(Set.of(MEDIUM));
        entityManager.flush();
        entityManager.clear();

        assertThat(storedMask(saved.getId())).isEqualTo(2);
    }

    @Test
    void everyDifficulty_isStoredWithinCheckConstraintAndReadBack() {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Set<OssIssueDifficulty> everyDifficulty = EnumSet.allOf(OssIssueDifficulty.class);

        OssRepoSubscription saved = ossRepoSubscriptionRepository.saveAndFlush(
                OssRepoSubscription.create(member, repo, everyDifficulty));
        entityManager.clear();

        assertThat(storedMask(saved.getId())).isEqualTo(OssDifficultyMask.of(everyDifficulty));
        assertThat(ossRepoSubscriptionRepository.findById(saved.getId()).orElseThrow().difficulties())
                .isEqualTo(everyDifficulty);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void maskOutsideOneToSeven_violatesCheckConstraint(int mask) {
        Member member = memberRepository.save(member("uid-1", "user@example.com"));
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO oss_repo_subscription (member_id, repo_id, difficulty_mask)"
                        + " VALUES (?1, ?2, ?3)")
                .setParameter(1, member.getId())
                .setParameter(2, repo.getId())
                .setParameter(3, mask)
                .executeUpdate())
                .rootCause()
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_oss_repo_subscription_difficulty_mask");
    }

    private Object storedMask(Long subscriptionId) {
        return entityManager.getEntityManager()
                .createNativeQuery("SELECT difficulty_mask FROM oss_repo_subscription WHERE id = ?1")
                .setParameter(1, subscriptionId)
                .getSingleResult();
    }

    private Member member(String providerUid, String email) {
        return Member.create("google", providerUid, email);
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, null, "Java", 1_000);
    }
}
