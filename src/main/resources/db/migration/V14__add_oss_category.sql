CREATE TABLE oss_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(40) NOT NULL,
    name_ko    VARCHAR(60) NOT NULL,
    name_en    VARCHAR(60) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_oss_category_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO oss_category (code, name_ko, name_en) VALUES
    ('ai-ml', 'AI와 머신러닝', 'AI & Machine Learning'),
    ('ai-agents', 'AI 에이전트와 LLM 도구', 'AI Agents & LLM Tools'),
    ('web-frontend', '웹 프론트엔드', 'Web Frontend'),
    ('backend', '백엔드와 API', 'Backend & APIs'),
    ('mobile', '모바일', 'Mobile'),
    ('desktop', '데스크톱 앱', 'Desktop Apps'),
    ('data', '데이터와 데이터베이스', 'Data & Databases'),
    ('devtools', '개발 도구', 'Developer Tools'),
    ('infra', '인프라와 클라우드', 'Infrastructure & Cloud'),
    ('security', '보안', 'Security'),
    ('languages', '언어와 런타임', 'Languages & Runtimes'),
    ('docs', '문서와 학습 자료', 'Docs & Learning'),
    ('games-media', '게임과 미디어', 'Games & Media');
