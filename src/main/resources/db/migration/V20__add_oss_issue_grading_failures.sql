ALTER TABLE oss_issue
    ADD COLUMN grading_failures            INT         NOT NULL DEFAULT 0 AFTER github_created_at,
    ADD COLUMN grading_failure_source_hash VARCHAR(64) NULL AFTER grading_failures;
