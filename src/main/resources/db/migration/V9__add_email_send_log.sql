-- email_send_log (다이제스트 발송 감사 로그 — 어떤 이메일에 언제 보냈는지)
-- notification과 달리 claim/수신거부로 지워지지 않는 영구 기록. 수신자 식별은 이메일 문자열로만 보관(FK 없음).
CREATE TABLE email_send_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    article_count INT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_email_send_log_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
