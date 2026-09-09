# ADR-0008: email_send_log 제거, 아웃박스 SENT 행을 발송 이력으로

## Status

Accepted (2026-08-27)

## Context

- 처음엔 발송 이력을 별도 테이블 `email_send_log`(Flyway V9)에 뒀다. 폴러가 발송에 성공하면 아웃박스 행을 `SENT`로 바꾸는 동시에 로그 테이블에도 한 줄을 남기는 **이중 쓰기**였다.
- fat row(ADR-0004) 덕에 아웃박스 행에는 발송에 관한 모든 게 이미 있었다 — 수신자, 유형, 제목, 글 수, 발송 시각(`sent_at`). `status = SENT`인 행이 곧 "이 이메일을 이때 보냈다"는 이력이고, `email_send_log`는 그 복제였다.

## Decision

`email_send_log`를 제거한다(Flyway **V12 DROP**). 발송 이력은 `notification_outbox WHERE status = SENT`로 조회한다(`OutboxAdminService.recentSent`). 디스패처의 이중 쓰기를 없앤다.

## Alternatives

- 별도 로그 테이블 유지: 같은 사실을 두 번 적는 이중 쓰기와 중복 저장이 남는다.

## Consequences

- (+) 이중 쓰기와 관리 포인트가 하나 줄었다. ADR-0004에서 "발송 로그로의 누수"로 봤던 `recipient_type`·`article_count`가 이제 **이력의 정당한 컬럼**이 됐다.
- (-) `SENT`를 안 지우므로 이력이 영구히 쌓인다(ADR-0004와 동일 부채).
- (=) 관리자 발송 로그 화면의 응답 shape·프론트는 그대로 두고 데이터 출처만 바꿨다.
- 재검토: 이력 용량이 문제되면 아카이빙/purge 도입.

> 원칙: 파생 상태를 별도 테이블로 승격하기 전에, 기존 행의 상태 전이로 표현되는지 먼저 의심한다.
