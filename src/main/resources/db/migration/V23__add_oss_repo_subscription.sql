CREATE TABLE oss_repo_subscription (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    member_id       BIGINT   NOT NULL,
    repo_id         BIGINT   NOT NULL,
    difficulty_mask INT      NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_repo_subscription_member_repo (member_id, repo_id),
    INDEX idx_oss_repo_subscription_repo_mask_member (repo_id, difficulty_mask, member_id),

    CONSTRAINT fk_oss_repo_subscription_member FOREIGN KEY (member_id) REFERENCES member(id)   ON DELETE CASCADE,
    CONSTRAINT fk_oss_repo_subscription_repo   FOREIGN KEY (repo_id)   REFERENCES oss_repo(id) ON DELETE CASCADE,
    CONSTRAINT ck_oss_repo_subscription_difficulty_mask CHECK (difficulty_mask BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
