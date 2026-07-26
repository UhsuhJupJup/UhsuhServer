CREATE TABLE bookmark (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    article_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_bookmark (member_id, article_id),
    INDEX idx_bookmark_member (member_id, created_at),

    CONSTRAINT fk_bookmark_member
        FOREIGN KEY (member_id)  REFERENCES member(id)  ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_article
        FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
