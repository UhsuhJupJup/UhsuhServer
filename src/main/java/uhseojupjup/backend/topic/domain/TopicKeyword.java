package uhseojupjup.backend.topic.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhseojupjup.backend.keyword.domain.Keyword;

@Entity
@Table(name = "topic_keyword")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    private TopicKeyword(Topic topic, Keyword keyword) {
        this.topic = topic;
        this.keyword = keyword;
    }

    public static TopicKeyword of(Topic topic, Keyword keyword) {
        return new TopicKeyword(topic, keyword);
    }
}
