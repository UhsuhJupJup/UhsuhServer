CREATE TABLE note_keyword (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    note_id    BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_note_keyword (note_id, keyword_id),
    INDEX idx_nk_keyword (keyword_id),

    CONSTRAINT fk_nk_note    FOREIGN KEY (note_id)    REFERENCES learning_note(id) ON DELETE CASCADE,
    CONSTRAINT fk_nk_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id)       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
