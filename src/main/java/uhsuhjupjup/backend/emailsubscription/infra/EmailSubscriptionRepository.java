package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;

import java.util.List;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {

    List<EmailSubscription> findByEmailSubscriberId(Long emailSubscriberId);

    void deleteByEmailSubscriberId(Long emailSubscriberId);
}
