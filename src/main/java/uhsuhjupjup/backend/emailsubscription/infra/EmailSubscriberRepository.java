package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;

import java.util.Optional;

public interface EmailSubscriberRepository extends JpaRepository<EmailSubscriber, Long> {

    Optional<EmailSubscriber> findByEmail(String email);

    Optional<EmailSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    boolean existsByEmail(String email);
}
