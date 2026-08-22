package uhsuhjupjup.backend.emailsubscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.emailsubscription.domain.EmailSubscription;
import uhsuhjupjup.backend.keyword.domain.Keyword;

import java.util.Collection;
import java.util.List;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscription, Long> {

    List<EmailSubscription> findByEmailSubscriberId(Long emailSubscriberId);

    @Query("select es.keyword from EmailSubscription es where es.emailSubscriber.id = :emailSubscriberId")
    List<Keyword> findKeywordsByEmailSubscriberId(Long emailSubscriberId);

    @Query("select es from EmailSubscription es join fetch es.keyword where es.emailSubscriber.id in :ids")
    List<EmailSubscription> findWithKeywordByEmailSubscriberIdIn(Collection<Long> ids);

    void deleteByEmailSubscriberId(Long emailSubscriberId);
}
