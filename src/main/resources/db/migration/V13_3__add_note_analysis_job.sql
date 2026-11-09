ALTER TABLE learning_note ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE note_analysis_job (
    note_id         BIGINT      NOT NULL,
    note_revision   BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    queued_at       DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_token     VARCHAR(36) NULL,
    lease_until     DATETIME(6) NULL,
    last_reason     VARCHAR(30) NULL,
    completed_at    DATETIME(6) NULL,
    acknowledged_at DATETIME(6) NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (note_id),
    KEY idx_note_analysis_job_status_next_attempt_at (status, next_attempt_at),
    KEY idx_note_analysis_job_status_lease_until (status, lease_until),
    CONSTRAINT fk_note_analysis_job_learning_note FOREIGN KEY (note_id) REFERENCES learning_note (id) ON DELETE CASCADE,
    CONSTRAINT ck_note_analysis_job_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
