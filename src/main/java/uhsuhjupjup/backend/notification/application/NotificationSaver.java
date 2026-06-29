package uhsuhjupjup.backend.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.notification.domain.Notification;
import uhsuhjupjup.backend.notification.infra.NotificationRepository;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationSaver {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ArticleRepository articleRepository;

    @Transactional
    public int record(Long memberId, Map<Long, String> matchedKeywordsByArticle) {
        if (matchedKeywordsByArticle.isEmpty()) {
            return 0;
        }
        Member memberRef = memberRepository.getReferenceById(memberId);
        List<Notification> rows = matchedKeywordsByArticle.entrySet().stream()
                .map(entry -> Notification.of(memberRef,
                        articleRepository.getReferenceById(entry.getKey()), entry.getValue()))
                .toList();
        notificationRepository.saveAll(rows);
        return rows.size();
    }
}
