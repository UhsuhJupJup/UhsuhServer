ALTER TABLE notification_outbox ADD COLUMN republish_at DATETIME NULL;

UPDATE notification_outbox SET republish_at = next_attempt_at;

ALTER TABLE notification_outbox MODIFY republish_at DATETIME NOT NULL;

CREATE INDEX idx_notification_outbox_republish ON notification_outbox (status, republish_at);
