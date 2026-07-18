ALTER TABLE article
    ADD COLUMN classified_at DATETIME NULL AFTER collected_at,
    ADD INDEX idx_article_classified (classified_at, collected_at);
