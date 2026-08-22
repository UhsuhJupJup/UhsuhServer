package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscriber;

import java.util.List;
import java.util.Optional;

public interface EmailSubscriberRepository extends JpaRepository<EmailSubscriber, Long> {

    List<EmailSubscriber> findAllByOrderByCreatedAtDesc();

    Optional<EmailSubscriber> findByEmail(String email);

    Optional<EmailSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    boolean existsByEmail(String email);
}
