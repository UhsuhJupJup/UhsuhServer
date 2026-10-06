CREATE TABLE oss_issue_grade (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    issue_id         BIGINT        NOT NULL,
    difficulty       VARCHAR(20)   NULL,
    problem          VARCHAR(20)   NOT NULL,
    reproduction     VARCHAR(20)   NOT NULL,
    cause            VARCHAR(20)   NOT NULL,
    fix_direction    VARCHAR(20)   NOT NULL,
    related_pr       BOOLEAN       NOT NULL,
    exclusion        VARCHAR(20)   NULL,
    reason_ko        VARCHAR(500)  NOT NULL,
    reason_en        VARCHAR(500)  NOT NULL,
    summary_ko       VARCHAR(1500) NULL,
    summary_en       VARCHAR(1500) NULL,
    criteria_version VARCHAR(40)   NOT NULL,
    model            VARCHAR(100)  NOT NULL,
    source_hash      VARCHAR(64)   NOT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_oss_issue_grade_issue (issue_id, id),

    CONSTRAINT fk_oss_issue_grade_issue FOREIGN KEY (issue_id) REFERENCES oss_issue(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
