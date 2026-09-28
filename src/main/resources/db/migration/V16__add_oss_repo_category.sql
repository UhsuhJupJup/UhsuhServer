CREATE TABLE oss_repo_category (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    repo_id     BIGINT NOT NULL,
    category_id BIGINT NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_repo_category (repo_id, category_id),
    INDEX idx_orc_category (category_id),

    CONSTRAINT fk_orc_repo     FOREIGN KEY (repo_id)     REFERENCES oss_repo(id)     ON DELETE CASCADE,
    CONSTRAINT fk_orc_category FOREIGN KEY (category_id) REFERENCES oss_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
