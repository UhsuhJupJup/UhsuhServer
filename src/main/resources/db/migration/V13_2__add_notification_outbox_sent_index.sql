CREATE INDEX idx_notification_outbox_sent ON notification_outbox (status, sent_at);
