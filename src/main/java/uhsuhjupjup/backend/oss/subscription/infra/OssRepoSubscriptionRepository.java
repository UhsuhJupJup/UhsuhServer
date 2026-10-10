package uhsuhjupjup.backend.oss.subscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.oss.subscription.domain.OssRepoSubscription;

import java.util.Optional;

public interface OssRepoSubscriptionRepository extends JpaRepository<OssRepoSubscription, Long> {

    Optional<OssRepoSubscription> findByMemberIdAndRepoId(Long memberId, Long repoId);
}
