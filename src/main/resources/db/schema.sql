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
