package uhsuhjupjup.backend.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.article.domain.Article;
import uhsuhjupjup.backend.article.infra.ArticleRepository;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.member.infra.MemberRepository;
import uhsuhjupjup.backend.notification.domain.Notification;
import uhsuhjupjup.backend.notification.infra.NotificationRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSaverTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private NotificationSaver saver;

    @Captor
    private ArgumentCaptor<List<Notification>> captor;

    @Test
    void record_savesNotificationPerArticle() {
        given(memberRepository.getReferenceById(1L)).willReturn(mock(Member.class));
        given(articleRepository.getReferenceById(10L)).willReturn(mock(Article.class));
        given(articleRepository.getReferenceById(20L)).willReturn(mock(Article.class));
        Map<Long, String> matched = new LinkedHashMap<>();
        matched.put(10L, "MySQL");
        matched.put(20L, "Kafka, Redis");

        int saved = saver.record(1L, matched);

        assertThat(saved).isEqualTo(2);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Notification::getMatchedKeywords)
                .containsExactly("MySQL", "Kafka, Redis");
    }

    @Test
    void record_whenEmpty_returnsZero() {
        int saved = saver.record(1L, Map.of());

        assertThat(saved).isZero();
        verify(memberRepository, never()).getReferenceById(anyLong());
    }
}
