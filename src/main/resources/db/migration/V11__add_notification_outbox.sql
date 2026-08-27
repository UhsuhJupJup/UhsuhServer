CREATE TABLE notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient VARCHAR(255) NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body MEDIUMTEXT NOT NULL,
    article_count INT NOT NULL,
    unsubscribe_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_notification_outbox_poll (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
