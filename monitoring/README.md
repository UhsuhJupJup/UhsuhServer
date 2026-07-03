# 로컬 모니터링 스택

Prometheus + Grafana + mysqld-exporter를 도커로 띄워 로컬 백엔드와 MySQL을 관측한다.

- 백엔드: 로컬 `bootRun`(`:8080`)을 `host.docker.internal:8080/actuator/prometheus`로 스크레이핑
- MySQL: `mysqld-exporter`가 `host.docker.internal:3306`으로 접속해 `:9104`로 메트릭 노출
- 배포 시에는 backend, MySQL, exporter를 같은 도커 네트워크로 묶는 별도 compose로 전환한다.

## 사전 준비

1. 백엔드가 실행 중이어야 한다(`./gradlew bootRun`, `:8080`).
2. exporter 전용 MySQL 계정을 관리자 계정으로 한 번 생성한다. 컨테이너에서 `host.docker.internal`로 접속하므로 `@'localhost'`가 아닌 `@'%'`여야 한다.

   ```sql
   CREATE USER 'exporter'@'%' IDENTIFIED BY 'change-me' WITH MAX_USER_CONNECTIONS 3;
   GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
   FLUSH PRIVILEGES;
   ```

3. `.env`를 만든다.

   ```bash
   cp .env.example .env
   ```

   `.env`의 `MYSQL_EXPORTER_USER` / `MYSQL_EXPORTER_PASSWORD`를 2번에서 만든 값으로, `GRAFANA_ADMIN_PASSWORD`를 원하는 값으로 채운다.

## 실행

```bash
docker compose up -d
```

## 확인

- Prometheus: http://localhost:9090 → Status → Targets 에서 `uhsuhjupjup-backend`, `mysqld-exporter` 가 UP
- exporter 직접: `curl http://localhost:9104/metrics`
- Grafana: http://localhost:3001 (admin / `GRAFANA_ADMIN_PASSWORD`) — 프론트(3000)와 겹치지 않게 3001로 노출
  1. Connections → Data sources → Prometheus 추가, URL `http://prometheus:9090`
  2. Dashboards → Import 로 커뮤니티 대시보드 사용
     - Spring Boot / JVM (Micrometer): `4701`, `11378`
     - MySQL (mysqld-exporter): `14057`, `7362`

## 종료

```bash
docker compose down      # 컨테이너만 제거(데이터 유지)
docker compose down -v   # 볼륨까지 제거
```
