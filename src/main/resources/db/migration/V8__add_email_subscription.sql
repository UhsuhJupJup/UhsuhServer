-- email_subscriber (비회원 이메일 구독자)
CREATE TABLE email_subscriber (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    verified_at       DATETIME     NULL,
    unsubscribe_token VARCHAR(36)  NOT NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_email_subscriber_email (email),
    UNIQUE KEY uk_email_subscriber_unsub (unsubscribe_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- email_subscription (비회원 키워드 구독)
CREATE TABLE email_subscription (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    email_subscriber_id BIGINT   NOT NULL,
    keyword_id          BIGINT   NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_emailsub (email_subscriber_id, keyword_id),
    INDEX idx_emailsub_fanout (keyword_id, email_subscriber_id),

    CONSTRAINT fk_emailsub_subscriber FOREIGN KEY (email_subscriber_id) REFERENCES email_subscriber(id) ON DELETE CASCADE,
    CONSTRAINT fk_emailsub_keyword    FOREIGN KEY (keyword_id)          REFERENCES keyword(id)          ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- notification 다형화: 회원 알림 + 비회원 알림을 한 테이블로
ALTER TABLE notification
    MODIFY COLUMN member_id BIGINT NULL,
    ADD COLUMN email_subscriber_id BIGINT NULL AFTER member_id,
    ADD CONSTRAINT fk_noti_email_subscriber FOREIGN KEY (email_subscriber_id) REFERENCES email_subscriber(id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_noti_email (email_subscriber_id, article_id),
    ADD CONSTRAINT ck_noti_recipient CHECK ((member_id IS NULL) <> (email_subscriber_id IS NULL));
