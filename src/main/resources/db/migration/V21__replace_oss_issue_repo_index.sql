ALTER TABLE oss_issue
    ADD INDEX idx_oss_issue_repo_github_created (repo_id, github_created_at, id),
    DROP INDEX idx_oss_issue_repo;
