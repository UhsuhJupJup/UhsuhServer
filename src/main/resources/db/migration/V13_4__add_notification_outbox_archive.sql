CREATE TABLE notification_outbox_archive (
    id              BIGINT       NOT NULL,
    recipient       VARCHAR(255) NOT NULL,
    recipient_type  VARCHAR(30)  NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body            MEDIUMTEXT   NOT NULL,
    article_count   INT          NOT NULL,
    unsubscribe_url VARCHAR(500) NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at DATETIME     NOT NULL,
    republish_at    DATETIME     NOT NULL,
    last_error      VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL,
    sent_at         DATETIME     NULL,
    archived_at     DATETIME     NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_notification_outbox_archive_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
