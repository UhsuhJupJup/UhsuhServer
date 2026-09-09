# ADR-0002: 스케줄 발화당 1회 보장에 ShedLock(리스) 채택

## Status

Accepted (2026-08-23) — supersedes [ADR-0001](0001-redis-lock-for-scheduler.md)

## Context

- ADR-0001의 `tryLock` + 즉시 `unlock`은 상호 배제만 보장했다.
- 실제로 08:00 알림 플로우가 **2번 실행**되는 것이 관측됐다(`pipeline_run`에 08:00:00 NOTIFICATION 2행). 인스턴스 간 시계 스큐로 A가 먼저 실행·해제한 뒤 B가 빈 락을 잡아 순차로 또 돈 것.
- Redis는 캐시로도 쓰여 `allkeys-lru` 정책이라, 락 키가 축출될 위험도 있었다.
- 우리가 원한 건 "동시 실행 금지"가 아니라 **"발화당 정확히 한 번(run-once)"**이었다.

## Decision

ShedLock을 도입한다. `notifyMembers`·`ingest`에 `@SchedulerLock(lockAtLeastFor=PT30S, lockAtMostFor=PT10M)`을 걸고, `JdbcTemplateLockProvider`에 `usingDbTime()`을 쓰며, 락은 DB 테이블(`shedlock`, Flyway V10)에 둔다. `lockAtLeastFor`가 빨리 끝나도 락을 최소 30초 유지해 시계 스큐를 덮는다.

## Alternatives

- Redis `SET NX EX` 가드(1차로 적용해 2행→1행 검증까지 했으나, 캐시로 쓰는 Redis와 한 살림이라 축출 위험이 남아 교체).
- DB 비관적 락: 락 대기·커넥션 점유.

## Consequences

- (+) **run-once 보장** — `lockAtLeastFor`가 스큐를 덮어 순차 중복을 막는다.
- (+) `usingDbTime()`으로 **DB 시계**를 기준 삼아 인스턴스 간 스큐를 제거한다.
- (+) **DB 기반 락**이라 캐시 Redis의 `allkeys-lru` 축출과 무관하다.
- (-) 라이브러리·`shedlock` 테이블이 추가된다.
- (=) `lockAtMostFor`(10분)는 태스크 최대 실행 시간보다 커야 한다(현재 배치는 1분 내).
- 재검토: 발화당 1회가 다시 깨지거나, 태스크가 `lockAtMostFor`를 넘게 길어지면.

## Compliance

로컬 2인스턴스 클러스터에서 크론을 5초 오프셋해 시계 스큐를 재현, 수정 전 주기당 2행 → 수정 후 1행을 확인했다.
