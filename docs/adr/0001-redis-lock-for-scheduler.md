# ADR-0001: 스케줄러 중복 실행 방지에 Redis 분산락(tryLock) 사용

## Status

Superseded by [ADR-0002](0002-shedlock-for-run-once-scheduling.md) (2026-08-09)

## Context

- 앱이 ALB 뒤 2대이고, `@Scheduled` 배치(6시 수집, 8시 알림)가 각 인스턴스에서 각각 실행된다.
- 두 인스턴스가 같은 배치를 실행하면 중복 수집·중복 발송이 될 수 있다.
- 이미 Redis를 (분산락 등으로) 쓰고 있어 추가 인프라 없이 락을 걸 수 있다.

## Decision

`RedisLockRegistry`로 `tryLock()`을 시도해, 락을 얻은 인스턴스만 배치를 실행하고 못 얻으면 스킵한다. 작업이 끝나면 `finally`에서 즉시 `unlock()` 한다.

## Alternatives

- DB 비관적 락(`SELECT ... FOR UPDATE`): DB만으로 완결되지만 락 대기·커넥션 점유.
- 방치(중복 허용): 알림이 인스턴스 수만큼 중복 발송될 수 있어 불가.

## Consequences

- (+) 두 인스턴스의 **동시** 실행을 차단한다. 추가 인프라 없이 단순하다.
- (-) `tryLock` + 즉시 `unlock`은 **상호 배제만** 보장하고 "발화당 정확히 한 번(run-once)"은 보장하지 못한다. 한 인스턴스가 빨리 끝나 락을 놓으면, 시계 스큐로 늦게 깨어난 다른 인스턴스가 빈 락을 잡아 **순차로 또 실행**할 수 있다.
- (=) 캐시로도 쓰는 Redis(`allkeys-lru`)에서는 락 키가 축출되면 상호 배제 자체가 깨질 수 있다.
- 재검토: 순차 중복 실행이 관측되면.

> 실제로 순차 중복(8시 알림 2회 실행)이 관측되어 [ADR-0002](0002-shedlock-for-run-once-scheduling.md)로 대체했다.
