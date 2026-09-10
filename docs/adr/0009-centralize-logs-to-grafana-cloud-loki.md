# ADR-0009: 로그를 Grafana Cloud Loki로 중앙 수집한다

## Status

Proposed

## Context

- 앱 인스턴스가 2대(`uhsuh-app-1` ap-northeast-2a, `uhsuh-app-2` ap-northeast-2b)로 늘었다. 사용자가 오류를 신고해도 어느 박스가 처리했는지 알 수 없어 두 박스를 모두 뒤져야 한다.
- 로그를 보는 유일한 수단이 `ssh` + `docker logs`다. 컨테이너를 재배포하면 이전 로그가 사라진다.
- `deploy/docker-compose.app.yml`·`docker-compose.data.yml` 어디에도 `logging:` 설정이 없다. 도커 기본 `json-file`은 **크기 제한이 없어** 사고 시 디스크가 찬다.
- 앱 로그는 Spring Boot 기본 평문이라 필드로 거를 수 없고, 요청을 관통하는 식별자가 없다.
- 메트릭은 데이터 박스의 `prometheus-agent`가 앱 박스 두 대를 **사설 IP로 원격 스크레이프**해 Grafana Cloud로 `remote_write` 한다(두 타깃 모두 정상). 다만 이 구성은 박스에서 직접 수정된 것이고 레포의 `prometheus/prometheus.cloud.yml`에는 반영되어 있지 않았다 — `monitoring/` 디렉터리는 CI 배포 대상이 아니라 **레포와 실제 박스가 갈라져 있다.**
- 박스 사양: 앱 t3.micro 1GB(현재 `JAVA_TOOL_OPTIONS: -Xms256m -Xmx384m`), 데이터 t3.small 2GB에 MySQL·Redis 상주.
- 운영 기록상 앱 박스에서 힙을 768m로 올렸을 때 커널 OOM으로 인스턴스가 리부팅 루프에 빠진 적이 있다. 여유 메모리가 얇다.

## Decision

로그를 **Grafana Cloud Loki**로 보낸다. 박스마다 **Grafana Alloy** 에이전트를 하나씩 두고, `json-file` 드라이버는 그대로 둔 채 도커 데몬의 로그 스트림(`loki.source.docker`)을 읽어 전송한다. 메트릭이 이미 Grafana Cloud에 있어 한 화면에서 이어보게 되고, 에이전트가 죽어도 앱은 영향을 받지 않는다.

## Alternatives

- **Loki 셀프호스팅(데이터 박스)**: 비용 0이지만 t3.small 2GB에 MySQL·Redis와 메모리를 다툰다. 더 큰 문제는 박스가 죽는 순간이 로그가 가장 필요한 순간인데 로그도 같이 죽는다는 점이다.
- **ELK(Elasticsearch + Kibana)**: 전문 검색은 가장 강하지만 Elasticsearch 단독으로도 이 박스들에 들어가지 않는다.
- **CloudWatch Logs (`awslogs` 드라이버)**: 관리형이고 AWS와 통합되지만 `docker logs`가 동작하지 않게 되고, 메트릭(Grafana Cloud)과 화면이 갈린다.
- **Loki 도커 로깅 드라이버**: 파일 tail 없이 바로 보내지만 마찬가지로 로컬 `docker logs`를 잃는다. 중앙이 안 보이는 상황에서 마지막 확인 수단이 사라진다.
- **현행 유지(SSH + `docker logs`)**: 인스턴스 1대일 때는 합리적이었으나 2대가 된 시점에서 조사 비용이 배가된다.

## Consequences

- (+) 인스턴스·레벨로 걸러 한 화면에서 검색한다. 컨테이너를 재배포해도 로그가 남는다.
- (+) `json-file` 드라이버를 유지하므로 `docker logs`가 그대로 동작한다. 중앙과 로컬 두 수단을 모두 갖는다.
- (+) 앱 박스의 Alloy가 **자기 박스의 백엔드를 로컬로** 스크레이프한다. 박스 간 네트워크 의존이 사라지고, 앱 박스를 늘려도 데이터 박스에 IP를 수동으로 추가할 필요가 없다. 데이터 박스의 원격 스크레이프는 중복이 되므로 제거하고 이 경로로 일원화한다.
- (-) **로그 로테이션이 선행 조건이 된다.** Alloy가 읽어가기 전에 디스크가 차면 아무 소용이 없다. `max-size`/`max-file`을 반드시 함께 넣는다.
- (-) **앱 박스 힙을 384m → 320m로 낮춘다.** 1GB에 에이전트가 추가되는데 여유가 얇다. 힙을 줄이는 대신 스왑 스래싱과 커널 OOM 위험을 피한다.
- (-) 외부 서비스 의존과 아웃바운드 트래픽이 늘어난다. 무료 티어 한도를 넘으면 비용이 발생한다.
- (-) **Alloy에 도커 소켓을 마운트한다(`:ro`).** 컨테이너 이름을 라벨로 붙이려면 데몬 API가 필요한데, 소켓 접근은 사실상 호스트 root 권한이다. `ro`는 소켓 파일 권한일 뿐 API 쓰기를 막지 못한다. 로그 수집 에이전트의 관례적 구성이지만 공격면이 늘어나는 것은 사실이다. 파일 tail(`/var/lib/docker/containers`)로 바꾸면 권한은 줄지만 컨테이너 이름 대신 컨테이너 ID만 남는다.
- (=) **요청 로그는 4xx, 5xx만 남긴다.** 성공 요청의 건수, 지연 분포는 이미 `http.server.requests`(SLO 버킷 50ms~5s, uri/method/status 태그)가 집계하고 있어 로그로 중복하지 않는다. 로그는 메트릭이 뭉개는 것(개별 요청의 식별자, 실제 URI)만 담당한다. 응답 헤더 `X-Request-Id`, `X-Instance`는 성공, 실패와 무관하게 항상 붙인다.
- (=) **라벨 카디널리티가 운영 규칙이 된다.** Loki는 라벨 조합 하나가 스트림 하나이므로 `job`·`instance`·`container`·`level`만 라벨로 쓰고, `requestId`·`traceId`·`memberId` 같은 값은 본문 필드에 두고 쿼리에서 거른다.
- (=) 운영 로그를 구조화(ECS JSON)로 바꾼다. Spring Boot 3.5의 내장 구조화 로깅을 쓰므로 추가 의존성은 없다. 로컬은 평문을 유지한다.
- (=) **Alloy 파싱 규칙이 Boot의 ECS 출력 모양에 묶인다.** 실측 결과 레벨은 평평한 `"log.level"` 키가 아니라 중첩된 `{"log":{"level":"WARN"}}`이고, MDC 값은 최상위 키로 실린다. Boot가 이 모양을 바꾸면 `level` 라벨이 조용히 비므로 계약 테스트로 고정한다.
- (=) 에이전트 실제 메모리 사용량은 도입 후 실측해 채운다. 이 문서에는 추정치를 적지 않는다.
- (=) **모니터링 설정을 CI 배포에 포함시켰다.** 레포와 박스의 드리프트가 이 ADR을 쓸 때 오판의 원인이었으므로, `monitoring/prometheus.cloud.yml`과 `docker-compose.grafana-cloud.yml`을 데이터 박스 배포 단계에서 함께 전송하고 `SIGHUP`으로 리로드한다. `.env`와 `prometheus/gc_token`은 레포에 없어 박스의 기존 파일이 유지된다.
- 재검토: 무료 티어 한도를 반복해 넘거나, 전문 검색·복잡한 집계가 필요해지면 보존기간 축소 또는 다른 백엔드를 다시 검토한다. 앱 박스를 t3.small로 올리면 힙 320m 제약은 해제한다.

## Compliance

- 도입 후 각 박스에서 `docker logs uhsuh-backend`가 여전히 동작하는지 확인한다.
- Grafana에서 `{instance="app1"}`과 `{instance="app2"}`가 각각 조회되는지, 같은 `requestId`로 한 요청의 로그가 이어지는지 확인한다.
- 앱 박스에서 힙 조정 후 `jvm_memory_used_bytes`와 OS 여유 메모리를 관찰한다.
- `EcsLogFormatContractTest`가 ECS 출력의 `log.level` 중첩 경로와 최상위 MDC 키를 검증한다. 이 테스트가 깨지면 Alloy `stage.json`도 함께 고쳐야 한다.
- 배포 파이프라인의 `test` 잡이 `promtool check config`와 `alloy validate`로 관측 설정을 검증한다. 깨진 설정은 배포에 도달하지 못한다.
- 대시보드의 LogQL은 `| json` 전체 추출이 아니라 **필요 필드만 명시 추출**한다. ECS의 `service.name`이 Loki 내장 `service_name` 라벨과 충돌해 추출 결과가 비결정적으로 손실되는 것을 실측으로 확인했다(전체 추출 8회 중 1회만 정상, 명시 추출 8/8).
