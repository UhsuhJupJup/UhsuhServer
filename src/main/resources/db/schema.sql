-- member
CREATE TABLE member (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(20)  NOT NULL,
    provider_uid      VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    consent_at        DATETIME     NULL,
    unsubscribe_token VARCHAR(36)  NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_provider (provider, provider_uid),
    UNIQUE KEY uk_member_unsub (unsubscribe_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- blog
CREATE TABLE blog (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    domain     VARCHAR(255) NOT NULL,
    rss_url    VARCHAR(500) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_blog_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- topic
CREATE TABLE topic (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- keyword
CREATE TABLE keyword (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_keyword_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- topic_keyword
CREATE TABLE topic_keyword (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    topic_id   BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_keyword (topic_id, keyword_id),
    INDEX idx_tk_keyword (keyword_id),

    CONSTRAINT fk_tk_topic   FOREIGN KEY (topic_id)   REFERENCES topic(id)   ON DELETE CASCADE,
    CONSTRAINT fk_tk_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- article
CREATE TABLE article (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    blog_id      BIGINT       NOT NULL,
    title        VARCHAR(500) NOT NULL,
    url          VARCHAR(512) NOT NULL,
    published_at DATETIME     NOT NULL,
    collected_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_article_url (url),
    INDEX idx_article_blog_pub (blog_id, published_at),
    INDEX idx_article_pub (published_at),

    CONSTRAINT fk_article_blog FOREIGN KEY (blog_id) REFERENCES blog(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- article_keyword (매칭 결과)
CREATE TABLE article_keyword (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    article_id  BIGINT      NOT NULL,
    keyword_id  BIGINT      NOT NULL,
    matched_via VARCHAR(20) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_article_keyword (article_id, keyword_id),
    INDEX idx_ak_keyword (keyword_id),

    CONSTRAINT fk_ak_article FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE,
    CONSTRAINT fk_ak_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- keyword_alias
CREATE TABLE keyword_alias (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    keyword_id BIGINT       NOT NULL,
    alias      VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_alias (alias),
    INDEX idx_alias_keyword (keyword_id),

    CONSTRAINT fk_alias_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- topic_subscription (토픽 구독)
CREATE TABLE topic_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    topic_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_topicsub (member_id, topic_id),
    INDEX idx_topicsub_fanout (topic_id, member_id),

    CONSTRAINT fk_topicsub_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_topicsub_topic  FOREIGN KEY (topic_id)  REFERENCES topic(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- keyword_subscription (키워드 구독)
CREATE TABLE keyword_subscription (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    keyword_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_kwsub (member_id, keyword_id),
    INDEX idx_kwsub_fanout (keyword_id, member_id),

    CONSTRAINT fk_kwsub_member  FOREIGN KEY (member_id)  REFERENCES member(id)  ON DELETE CASCADE,
    CONSTRAINT fk_kwsub_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
