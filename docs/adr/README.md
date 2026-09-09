# Architecture Decision Records

ADR은 코드만으로 알기 어려운 **기술 결정의 이유와 감수한 결과**를 남기는 기록이다. 완벽한 결정만 적지 않는다 — 단점·위험을 알고도 선택한 타협이면 그 이유와 재검토 조건을 함께 남긴다.

작성 규칙·형식은 `.claude/skills/adr` 스킬을 따른다. 파일명은 `NNNN-short-english-title.md`, 한 ADR에 결정 하나, 결정이 바뀌면 덮어쓰지 않고 새 ADR로 대체(supersede)한다.

## 현재 ADR 목록

| 번호 | 결정 | 상태 | 한 줄 요약 |
| --- | --- | --- | --- |
| [0001](0001-redis-lock-for-scheduler.md) | 스케줄러 Redis 분산락(tryLock) | Superseded by 0002 | 동시 실행은 막지만 run-once는 보장 못 함 |
| [0002](0002-shedlock-for-run-once-scheduling.md) | 스케줄 run-once에 ShedLock(리스) | Accepted | lockAtLeastFor로 시계 스큐를 덮어 발화당 1회 보장 |
| [0003](0003-transactional-outbox-for-digest.md) | 다이제스트 발송 트랜잭셔널 아웃박스(폴링) | Accepted | 적재 후 폴러 발송으로 무유실(at-least-once) |
| [0004](0004-fat-row-in-outbox.md) | 아웃박스 fat row 저장 | Accepted | 렌더된 이메일을 통째 저장해 결정 시점 동결, 멱등 재시도 |
| [0005](0005-poller-interval-60s.md) | 폴러 주기 60초 | Accepted | 하루 1버스트 워크로드에 맞춰 15초→60초, kick은 배제 |
| [0006](0006-dlq-error-classification-retry-horizon.md) | DLQ + 오류 분류 + 재시도 지평 | Accepted | status=FAILED, 영구/일시 분류, ~6h 재시도, Grafana 알림 |
| [0007](0007-silent-loss-mitigation-loud-over-selfheal.md) | 조용한 유실 대응 B안 채택, A안 보류 | Accepted | 재시도+알림으로 무음 유실 제거, 자가치유(A)는 보류 |
| [0008](0008-drop-email-send-log-sent-as-history.md) | email_send_log 제거, SENT=이력 | Accepted | 파생 로그를 아웃박스 상태 전이로 흡수 |

## 작성 후보 (미작성)

아래는 ADR 기준을 만족할 수 있는 과거 결정이지만, **작성자가 당시 맥락·대안·감수한 점을 직접 확인해 채워야 한다**(추측으로 근거를 지어내지 않기 위해 여기 후보로만 남긴다).

| 결정 | 시점(커밋) | 메모 |
| --- | --- | --- |
| 메일 발송을 Gmail SMTP → AWS SES 자체 도메인으로 전환 | 2026-08-21 (`52181e2`) | 도달성·발송 한도·자체 도메인 인증(SPF/DKIM) 근거 필요 |
| 알림 fan-out을 키워드 구독만으로 단일화 | 2026-07-29 (`af6e550`) | 토픽 구독은 알림 미발송으로 둔 이유·topic_subscription 도먼트 보존 |
| 로그인 없는 비회원 이메일 구독 + notification 다형화 | 2026-08-23 (`9d1bf70`) | member XOR email_subscriber, claim 시 re-key 결정 근거 |
| 회원당 알림 최대 5개 상한 | 2026-07-20 (`22925bf`) | 상한 값·최근 수집순 기준 근거 |

> 후속 과제(아직 결정 전이라 ADR 아님): reconciliation(발송-결정 대사)으로 ADR-0007의 잔여 유실 닫기, 바운스/컴플레인 웹훅으로 실제 도달 추적, 아웃박스 SENT purge/아카이빙.
