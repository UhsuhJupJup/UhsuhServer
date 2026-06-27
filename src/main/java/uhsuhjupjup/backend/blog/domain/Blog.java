package uhsuhjupjup.backend.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uhsuhjupjup.backend.common.domain.BaseEntity;

@Entity
@Table(name = "blog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Blog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @Column(name = "rss_url", nullable = false, length = 500)
    private String rssUrl;

    @Column(name = "active", nullable = false)
    private boolean active;

    private Blog(String name, String domain, String rssUrl) {
        this.name = name;
        this.domain = domain;
        this.rssUrl = rssUrl;
        this.active = true;
    }

    public static Blog create(String name, String domain, String rssUrl) {
        return new Blog(name, domain, rssUrl);
    }

    public void deactivate() {
        this.active = false;
    }
}
