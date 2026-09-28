CREATE TABLE oss_issue (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id           BIGINT       NOT NULL,
    github_issue_id   BIGINT       NOT NULL,
    number            INT          NOT NULL,
    title             VARCHAR(256) NOT NULL,
    body_hash         VARCHAR(64)  NOT NULL,
    github_created_at DATETIME     NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_issue_github_issue_id (github_issue_id),
    INDEX idx_oss_issue_repo (repo_id),

    CONSTRAINT fk_oss_issue_repo FOREIGN KEY (repo_id) REFERENCES oss_repo(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE oss_repo_sync_state (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    repo_id               BIGINT       NOT NULL,
    etag                  VARCHAR(255) NULL,
    last_issue_updated_at DATETIME     NULL,
    last_synced_at        DATETIME     NULL,
    consecutive_failures  INT          NOT NULL DEFAULT 0,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_repo_sync_state_repo_id (repo_id),

    CONSTRAINT fk_oss_repo_sync_state_repo FOREIGN KEY (repo_id) REFERENCES oss_repo(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
