package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.keyword.domain.Keyword;

import java.util.List;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {

    List<EmailSubscription> findByEmailSubscriberId(Long emailSubscriberId);

    @Query("select es.keyword from EmailSubscription es where es.emailSubscriber.id = :emailSubscriberId")
    List<Keyword> findKeywordsByEmailSubscriberId(Long emailSubscriberId);

    void deleteByEmailSubscriberId(Long emailSubscriberId);
}
