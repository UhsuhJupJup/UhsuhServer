# ADR-0003: 다이제스트 발송에 트랜잭셔널 아웃박스(폴링) 도입

## Status

Accepted (2026-08-27)

## Context

- 08:00 다이제스트 알림을 팬아웃 루프 안에서 `emailSender.send()`로 **동기 발송**하고 있었다. 발송 작업이 메모리에만 있어, 크래시·재기동·SMTP 일시 장애 시 발송 요청이 그대로 유실됐다(at-most-once).
- "DB에 발송 기록"과 "SMTP 발송"은 서로 다른 시스템이라 하나의 트랜잭션으로 묶을 수 없다(dual-write). Graceful Shutdown은 타임아웃·강제 종료에 불충분하다.
- 워크로드는 하루 1버스트, 소볼륨, 단일 MySQL이다. 목표는 **무유실(at-least-once)**.

## Decision

발송을 직접 하지 않고 `notification_outbox`(Flyway V11)에 `PENDING`으로 적재한다. 이 적재를 비즈니스 기록(`Notification`)과 **같은 트랜잭션**으로 커밋하고(`OutboxEnqueuer`), 별도 워커(`OutboxScheduler`)가 `PENDING`을 폴링해 발송한다. 폴링 방식(Polling Publisher)을 택한다.

## Alternatives

- 아웃박스 + CDC(Debezium): 폴링 없이 binlog로 즉시 릴레이하지만 Kafka·Debezium 인프라가 과하다.
- 메시지 브로커(SQS/Kafka): dual-write가 DB↔브로커로 옮겨갈 뿐 사라지지 않아 결국 아웃박스와 결합해야 한다.
- Durable execution(Temporal): 견고하지만 규모 대비 운영·러닝커브가 과하다.
- 동기 발송 + Graceful Shutdown: 타임아웃·크래시에 유실을 못 막는다.

## Consequences

- (+) 크래시·재기동·SMTP 일시 장애에도 적재분은 유실되지 않고 재처리된다(at-least-once).
- (-) 폴 주기만큼 발송 지연, 발송 후 기록 전 크래시 시 중복 가능(→ 멱등/드문 중복 감수).
- (=) `notification_outbox` 테이블과 폴러를 운영해야 한다.
- 재검토: 저지연·고볼륨 요구가 생기면 CDC/브로커로.
