package uhseojupjup.backend.keyword.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhseojupjup.backend.common.domain.BaseEntity;

@Entity
@Table(name = "keyword_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordAlias extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword_id", nullable = false)
    private Long keywordId;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    private KeywordAlias(Long keywordId, String alias) {
        this.keywordId = keywordId;
        this.alias = alias;
    }

    public static KeywordAlias create(Long keywordId, String alias) {
        return new KeywordAlias(keywordId, alias);
    }
}
