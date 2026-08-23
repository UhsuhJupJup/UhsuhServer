-- ShedLock: 다중 인스턴스에서 @Scheduled 작업을 스케줄당 1회만 실행하기 위한 락 테이블.
-- JdbcTemplateLockProvider가 name을 PK로 각 스케줄의 락을 관리한다(JPA 엔티티 아님 → ddl-auto=validate 대상 아님).
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
