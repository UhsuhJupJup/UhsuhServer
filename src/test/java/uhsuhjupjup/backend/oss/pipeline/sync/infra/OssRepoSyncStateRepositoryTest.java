package uhsuhjupjup.backend.oss.pipeline.sync.infra;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssRepoSyncState;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MySqlDataJpaTest
class OssRepoSyncStateRepositoryTest {

    @Autowired
    private OssRepoSyncStateRepository ossRepoSyncStateRepository;

    @Autowired
    private OssRepoRepository ossRepoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByRepoId_returnsOnlyThatReposState() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        Long springBootState = ossRepoSyncStateRepository.save(OssRepoSyncState.create(springBoot)).getId();
        ossRepoSyncStateRepository.save(OssRepoSyncState.create(react));
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoSyncStateRepository.findByRepoId(springBoot.getId()))
                .map(OssRepoSyncState::getId)
                .hasValue(springBootState);
    }

    @Test
    void findByRepoId_repoNeverSynced_returnsEmpty() {
        OssRepo repo = ossRepoRepository.saveAndFlush(repo(1L, "spring-projects/spring-boot"));
        entityManager.clear();

        assertThat(ossRepoSyncStateRepository.findByRepoId(repo.getId())).isEmpty();
    }

    @Test
    void create_startsWithoutEtagOrSyncTimesAndWithNoFailures() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        Long id = ossRepoSyncStateRepository.saveAndFlush(OssRepoSyncState.create(repo)).getId();
        entityManager.clear();

        OssRepoSyncState loaded = ossRepoSyncStateRepository.findById(id).orElseThrow();

        assertThat(loaded.getRepo().getId()).isEqualTo(repo.getId());
        assertThat(loaded.getEtag()).isNull();
        assertThat(loaded.getLastIssueUpdatedAt()).isNull();
        assertThat(loaded.getLastSyncedAt()).isNull();
        assertThat(loaded.getConsecutiveFailures()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByRepoId_loadsStateWithoutLoadingRepo() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoSyncStateRepository.saveAndFlush(OssRepoSyncState.create(repo));
        entityManager.clear();

        OssRepoSyncState loaded = ossRepoSyncStateRepository.findByRepoId(repo.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getRepo())).isFalse();
    }

    @Test
    void secondStateForSameRepo_violatesUniqueConstraint() {
        OssRepo repo = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        ossRepoSyncStateRepository.saveAndFlush(OssRepoSyncState.create(repo));

        assertThatThrownBy(() -> ossRepoSyncStateRepository.saveAndFlush(OssRepoSyncState.create(repo)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_oss_repo_sync_state_repo_id");
    }

    @Test
    void deletingRepo_deletesItsStateOnly() {
        OssRepo springBoot = ossRepoRepository.save(repo(1L, "spring-projects/spring-boot"));
        OssRepo react = ossRepoRepository.save(repo(2L, "facebook/react"));
        ossRepoSyncStateRepository.save(OssRepoSyncState.create(springBoot));
        ossRepoSyncStateRepository.save(OssRepoSyncState.create(react));
        entityManager.flush();
        entityManager.clear();

        ossRepoRepository.deleteById(springBoot.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(ossRepoSyncStateRepository.findByRepoId(springBoot.getId())).isEmpty();
        assertThat(ossRepoSyncStateRepository.findByRepoId(react.getId())).isPresent();
    }

    private OssRepo repo(Long githubId, String fullName) {
        return OssRepo.create(githubId, fullName, null, "Java", 1_000);
    }
}
