CREATE TABLE oss_repo (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    github_id        BIGINT       NOT NULL,
    full_name        VARCHAR(140) NOT NULL,
    full_name_key    VARCHAR(140) NOT NULL,
    description      TEXT         NULL,
    summary_ko       TEXT         NULL,
    summary_en       TEXT         NULL,
    primary_language VARCHAR(100) NULL,
    stars            INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_repo_github_id (github_id),
    UNIQUE KEY uk_oss_repo_full_name_key (full_name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
