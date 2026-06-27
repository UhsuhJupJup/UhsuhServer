package uhsuhjupjup.backend.subscription.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.subscription.domain.KeywordSubscription;

import java.util.Collection;
import java.util.List;

public interface KeywordSubscriptionRepository extends JpaRepository<KeywordSubscription, Long> {

    @Query("select ks.keyword from KeywordSubscription ks where ks.member.id = :memberId order by ks.keyword.name")
    List<Keyword> findSubscribedKeywords(Long memberId);

    void deleteByMemberIdAndKeywordIdIn(Long memberId, Collection<Long> keywordIds);

    void deleteByMemberId(Long memberId);
}
