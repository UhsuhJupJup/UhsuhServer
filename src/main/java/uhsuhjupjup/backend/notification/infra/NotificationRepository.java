package uhsuhjupjup.backend.notification.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uhsuhjupjup.backend.notification.application.dto.RecipientPair;
import uhsuhjupjup.backend.notification.domain.Notification;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select new uhsuhjupjup.backend.notification.application.dto.RecipientPair(ks.member.id, ak.article.id)
            from ArticleKeyword ak, KeywordSubscription ks
            where ks.keyword.id = ak.keyword.id
              and ak.article.collectedAt >= :threshold
              and not exists (select 1 from Notification n
                              where n.member.id = ks.member.id and n.article.id = ak.article.id)
            """)
    List<RecipientPair> findKeywordPathRecipients(LocalDateTime threshold);

    @Query("""
            select new uhsuhjupjup.backend.notification.application.dto.RecipientPair(ts.member.id, ak.article.id)
            from ArticleKeyword ak, TopicKeyword tk, TopicSubscription ts
            where tk.keyword.id = ak.keyword.id
              and ts.topic.id = tk.topic.id
              and ak.article.collectedAt >= :threshold
              and not exists (select 1 from Notification n
                              where n.member.id = ts.member.id and n.article.id = ak.article.id)
            """)
    List<RecipientPair> findTopicPathRecipients(LocalDateTime threshold);
}
