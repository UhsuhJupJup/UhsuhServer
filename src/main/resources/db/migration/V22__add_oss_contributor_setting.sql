CREATE TABLE oss_contributor_setting (
    member_id     BIGINT      NOT NULL,
    language      VARCHAR(10) NOT NULL,
    email_enabled BOOLEAN     NOT NULL,
    push_enabled  BOOLEAN     NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (member_id),

    CONSTRAINT fk_oss_contributor_setting_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
