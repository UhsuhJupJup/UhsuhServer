INSERT IGNORE INTO blog (name, domain, rss_url, active) VALUES
  ('우아한형제들', 'techblog.woowahan.com', 'https://techblog.woowahan.com/feed/', TRUE),
  ('토스', 'toss.tech', 'https://toss.tech/rss.xml', TRUE),
  ('카카오', 'tech.kakao.com', 'https://tech.kakao.com/feed/', TRUE),
  ('네이버 D2', 'd2.naver.com', 'https://d2.naver.com/d2.atom', TRUE),
  ('하이퍼커넥트', 'hyperconnect.github.io', 'https://hyperconnect.github.io/feed.xml', TRUE),
  ('NHN 클라우드', 'meetup.nhncloud.com', 'https://meetup.nhncloud.com/rss', TRUE),
  ('라인', 'techblog.lycorp.co.jp', 'https://techblog.lycorp.co.jp/ko/feed/index.xml', TRUE),
  ('인프랩', 'tech.inflab.com', 'https://tech.inflab.com/rss.xml', TRUE),
  ('여기어때', 'techblog.gccompany.co.kr', 'https://techblog.gccompany.co.kr/feed', TRUE),
  ('AWS 코리아', 'aws.amazon.com/ko/blogs/tech', 'https://aws.amazon.com/ko/blogs/tech/feed/', TRUE),
  ('KT 클라우드', 'tech.ktcloud.com', 'https://tech.ktcloud.com/rss/', TRUE),
  ('카카오뱅크', 'tech.kakaobank.com', 'https://tech.kakaobank.com/index.xml', TRUE),
  ('당근', 'medium.com/daangn', 'https://medium.com/feed/daangn', TRUE),
  ('쿠팡', 'medium.com/coupang-engineering', 'https://medium.com/feed/coupang-engineering', TRUE),
  ('왓챠', 'medium.com/watcha', 'https://medium.com/feed/watcha', TRUE),
  ('직방', 'medium.com/zigbang', 'https://medium.com/feed/zigbang', TRUE),
  ('네이버 플레이스', 'medium.com/naver-place-dev', 'https://medium.com/feed/naver-place-dev', TRUE),
  ('마이리얼트립', 'blog.myrealtrip.com', 'https://blog.myrealtrip.com/rss/', TRUE),
  ('원티드', 'medium.com/wantedjobs', 'https://medium.com/feed/wantedjobs', TRUE),
  ('콴다', 'medium.com/mathpresso', 'https://medium.com/feed/mathpresso', TRUE),
  ('무신사', 'medium.com/musinsa-tech', 'https://medium.com/feed/musinsa-tech', TRUE);


INSERT IGNORE INTO topic (name) VALUES
  ('백엔드'),
  ('프론트엔드'),
  ('데이터베이스'),
  ('인프라'),
  ('데이터 엔지니어링'),
  ('AI'),
  ('모바일'),
  ('아키텍처'),
  ('테스트'),
  ('개발 문화');


INSERT IGNORE INTO keyword (name) VALUES
  ('Spring'),('Spring Boot'),('JPA'),('Kafka'),('Redis'),('gRPC'),('GraphQL'),('Kotlin'),('Java'),('동시성'),('트랜잭션'),
  ('React'),('Next.js'),('TypeScript'),('Vue'),('상태관리'),('웹 성능'),('Webpack'),('Tailwind'),('웹 접근성'),('렌더링'),
  ('MySQL'),('PostgreSQL'),('MongoDB'),('인덱스'),('쿼리 최적화'),('샤딩'),('복제'),('데드락'),('Elasticsearch'),('커넥션 풀'),
  ('Kubernetes'),('Docker'),('AWS'),('Terraform'),('CI/CD'),('모니터링'),('Prometheus'),('Grafana'),('무중단 배포'),('서비스 메시'),
  ('Spark'),('Airflow'),('데이터 파이프라인'),('ETL'),('데이터 레이크'),('BigQuery'),('스트리밍'),('CDC'),('dbt'),
  ('머신러닝'),('딥러닝'),('LLM'),('생성형 AI'),('AI 에이전트'),('추천 시스템'),('MLOps'),('임베딩'),('파인튜닝'),('벡터 DB'),('PyTorch'),
  ('Android'),('iOS'),('Swift'),('SwiftUI'),('Jetpack Compose'),('Flutter'),('React Native'),('앱 성능'),
  ('MSA'),('DDD'),('이벤트 기반'),('분산 시스템'),('대용량 트래픽'),('성능 최적화'),('캐시'),('헥사고날'),('이벤트 소싱'),('CQRS'),
  ('테스트'),('단위 테스트'),('통합 테스트'),('E2E 테스트'),('TDD'),('테스트 코드'),('테스트 자동화'),('JUnit'),('Mock'),('부하 테스트'),
  ('코드 리뷰'),('애자일'),('스크럼'),('회고'),('기술 부채'),('리팩터링'),('온보딩'),('생산성'),('페어 프로그래밍'),('문서화');


INSERT IGNORE INTO keyword_alias (keyword_id, alias)
SELECT k.id, a.alias FROM keyword k JOIN (
  SELECT 'Spring' name, '스프링' alias UNION ALL
  SELECT 'Spring Boot','스프링부트' UNION ALL SELECT 'Spring Boot','springboot' UNION ALL
  SELECT 'Kafka','카프카' UNION ALL
  SELECT 'Redis','레디스' UNION ALL
  SELECT 'Kotlin','코틀린' UNION ALL
  SELECT 'Java','자바' UNION ALL
  SELECT '동시성','concurrency' UNION ALL
  SELECT '트랜잭션','transaction' UNION ALL
  SELECT 'React','리액트' UNION ALL SELECT 'React','reactjs' UNION ALL
  SELECT 'Next.js','nextjs' UNION ALL SELECT 'Next.js','넥스트' UNION ALL
  SELECT 'TypeScript','타입스크립트' UNION ALL
  SELECT 'Vue','vuejs' UNION ALL
  SELECT '상태관리','state management' UNION ALL
  SELECT '웹 성능','web performance' UNION ALL
  SELECT 'Webpack','웹팩' UNION ALL
  SELECT 'Tailwind','tailwindcss' UNION ALL SELECT 'Tailwind','테일윈드' UNION ALL
  SELECT '웹 접근성','a11y' UNION ALL SELECT '웹 접근성','accessibility' UNION ALL SELECT '웹 접근성','접근성' UNION ALL
  SELECT '렌더링','rendering' UNION ALL
  SELECT 'PostgreSQL','postgres' UNION ALL SELECT 'PostgreSQL','포스트그레스' UNION ALL
  SELECT 'MongoDB','몽고db' UNION ALL SELECT 'MongoDB','몽고디비' UNION ALL
  SELECT '인덱스','index' UNION ALL
  SELECT '쿼리 최적화','query optimization' UNION ALL SELECT '쿼리 최적화','쿼리 튜닝' UNION ALL
  SELECT '샤딩','sharding' UNION ALL
  SELECT '복제','replication' UNION ALL
  SELECT '데드락','deadlock' UNION ALL SELECT '데드락','교착상태' UNION ALL
  SELECT 'Elasticsearch','엘라스틱서치' UNION ALL
  SELECT '커넥션 풀','connection pool' UNION ALL SELECT '커넥션 풀','커넥션풀' UNION ALL SELECT '커넥션 풀','hikaricp' UNION ALL
  SELECT 'Kubernetes','쿠버네티스' UNION ALL SELECT 'Kubernetes','k8s' UNION ALL SELECT 'Kubernetes','쿠버' UNION ALL
  SELECT 'Docker','도커' UNION ALL
  SELECT 'Terraform','테라폼' UNION ALL
  SELECT 'CI/CD','cicd' UNION ALL SELECT 'CI/CD','ci-cd' UNION ALL
  SELECT '모니터링','monitoring' UNION ALL
  SELECT 'Prometheus','프로메테우스' UNION ALL
  SELECT 'Grafana','그라파나' UNION ALL
  SELECT '무중단 배포','zero-downtime' UNION ALL SELECT '무중단 배포','블루그린' UNION ALL SELECT '무중단 배포','무중단' UNION ALL
  SELECT '서비스 메시','service mesh' UNION ALL SELECT '서비스 메시','istio' UNION ALL SELECT '서비스 메시','이스티오' UNION ALL
  SELECT 'Spark','스파크' UNION ALL
  SELECT 'Airflow','에어플로우' UNION ALL
  SELECT '데이터 파이프라인','data pipeline' UNION ALL
  SELECT '데이터 레이크','data lake' UNION ALL SELECT '데이터 레이크','데이터레이크' UNION ALL
  SELECT 'BigQuery','빅쿼리' UNION ALL
  SELECT '스트리밍','streaming' UNION ALL
  SELECT 'CDC','change data capture' UNION ALL SELECT 'CDC','변경 데이터 캡처' UNION ALL
  SELECT '머신러닝','machine learning' UNION ALL SELECT '머신러닝','머신 러닝' UNION ALL
  SELECT '딥러닝','deep learning' UNION ALL SELECT '딥러닝','딥 러닝' UNION ALL
  SELECT 'LLM','거대언어모델' UNION ALL SELECT 'LLM','대규모 언어 모델' UNION ALL
  SELECT '생성형 AI','generative ai' UNION ALL SELECT '생성형 AI','genai' UNION ALL
  SELECT 'AI 에이전트','ai agent' UNION ALL SELECT 'AI 에이전트','ai에이전트' UNION ALL
  SELECT '추천 시스템','recommendation' UNION ALL SELECT '추천 시스템','추천시스템' UNION ALL
  SELECT 'MLOps','엠엘옵스' UNION ALL
  SELECT '임베딩','embedding' UNION ALL
  SELECT '파인튜닝','fine-tuning' UNION ALL SELECT '파인튜닝','파인 튜닝' UNION ALL
  SELECT '벡터 DB','vector db' UNION ALL SELECT '벡터 DB','vectordb' UNION ALL SELECT '벡터 DB','벡터 데이터베이스' UNION ALL
  SELECT 'PyTorch','파이토치' UNION ALL
  SELECT 'Android','안드로이드' UNION ALL
  SELECT 'Swift','스위프트' UNION ALL
  SELECT 'SwiftUI','스위프트ui' UNION ALL
  SELECT 'Jetpack Compose','컴포즈' UNION ALL SELECT 'Jetpack Compose','jetpack compose' UNION ALL
  SELECT 'Flutter','플러터' UNION ALL
  SELECT 'React Native','리액트 네이티브' UNION ALL SELECT 'React Native','react-native' UNION ALL
  SELECT '앱 성능','app performance' UNION ALL
  SELECT 'MSA','마이크로서비스' UNION ALL SELECT 'MSA','microservices' UNION ALL
  SELECT 'DDD','도메인 주도 설계' UNION ALL SELECT 'DDD','domain-driven' UNION ALL
  SELECT '이벤트 기반','event-driven' UNION ALL SELECT '이벤트 기반','이벤트 드리븐' UNION ALL SELECT '이벤트 기반','이벤트기반' UNION ALL
  SELECT '분산 시스템','distributed system' UNION ALL SELECT '분산 시스템','분산시스템' UNION ALL
  SELECT '대용량 트래픽','대규모 트래픽' UNION ALL
  SELECT '성능 최적화','performance optimization' UNION ALL SELECT '성능 최적화','성능 개선' UNION ALL
  SELECT '캐시','cache' UNION ALL SELECT '캐시','caching' UNION ALL SELECT '캐시','캐싱' UNION ALL
  SELECT '헥사고날','hexagonal' UNION ALL SELECT '헥사고날','헥사고날 아키텍처' UNION ALL
  SELECT '이벤트 소싱','event sourcing' UNION ALL
  SELECT '단위 테스트','unit test' UNION ALL SELECT '단위 테스트','유닛 테스트' UNION ALL
  SELECT '통합 테스트','integration test' UNION ALL
  SELECT 'E2E 테스트','e2e' UNION ALL SELECT 'E2E 테스트','end-to-end' UNION ALL
  SELECT 'TDD','테스트 주도 개발' UNION ALL
  SELECT '테스트 코드','test code' UNION ALL
  SELECT '테스트 자동화','test automation' UNION ALL
  SELECT 'JUnit','제이유닛' UNION ALL
  SELECT 'Mock','모킹' UNION ALL SELECT 'Mock','mockito' UNION ALL
  SELECT '부하 테스트','load test' UNION ALL SELECT '부하 테스트','로드 테스트' UNION ALL
  SELECT '코드 리뷰','code review' UNION ALL SELECT '코드 리뷰','코드리뷰' UNION ALL
  SELECT '애자일','agile' UNION ALL
  SELECT '스크럼','scrum' UNION ALL
  SELECT '회고','retrospective' UNION ALL
  SELECT '기술 부채','technical debt' UNION ALL SELECT '기술 부채','tech debt' UNION ALL SELECT '기술 부채','기술부채' UNION ALL
  SELECT '리팩터링','refactoring' UNION ALL SELECT '리팩터링','리팩토링' UNION ALL
  SELECT '온보딩','onboarding' UNION ALL
  SELECT '생산성','productivity' UNION ALL
  SELECT '페어 프로그래밍','pair programming' UNION ALL SELECT '페어 프로그래밍','페어프로그래밍' UNION ALL
  SELECT '문서화','documentation'
) a ON a.name = k.name;


INSERT IGNORE INTO topic_keyword (topic_id, keyword_id)
SELECT t.id, k.id FROM (
  SELECT '백엔드' topic,'Spring' keyword UNION ALL
  SELECT '백엔드','Spring Boot' UNION ALL SELECT '백엔드','JPA' UNION ALL SELECT '백엔드','Kafka' UNION ALL
  SELECT '백엔드','Redis' UNION ALL SELECT '백엔드','gRPC' UNION ALL SELECT '백엔드','GraphQL' UNION ALL
  SELECT '백엔드','Kotlin' UNION ALL SELECT '백엔드','Java' UNION ALL SELECT '백엔드','동시성' UNION ALL SELECT '백엔드','트랜잭션' UNION ALL
  SELECT '프론트엔드','React' UNION ALL SELECT '프론트엔드','Next.js' UNION ALL SELECT '프론트엔드','TypeScript' UNION ALL
  SELECT '프론트엔드','Vue' UNION ALL SELECT '프론트엔드','상태관리' UNION ALL SELECT '프론트엔드','웹 성능' UNION ALL
  SELECT '프론트엔드','Webpack' UNION ALL SELECT '프론트엔드','Tailwind' UNION ALL SELECT '프론트엔드','웹 접근성' UNION ALL SELECT '프론트엔드','렌더링' UNION ALL
  SELECT '데이터베이스','MySQL' UNION ALL SELECT '데이터베이스','PostgreSQL' UNION ALL SELECT '데이터베이스','MongoDB' UNION ALL
  SELECT '데이터베이스','인덱스' UNION ALL SELECT '데이터베이스','쿼리 최적화' UNION ALL SELECT '데이터베이스','샤딩' UNION ALL
  SELECT '데이터베이스','복제' UNION ALL SELECT '데이터베이스','데드락' UNION ALL SELECT '데이터베이스','Elasticsearch' UNION ALL SELECT '데이터베이스','커넥션 풀' UNION ALL
  SELECT '인프라','Kubernetes' UNION ALL SELECT '인프라','Docker' UNION ALL SELECT '인프라','AWS' UNION ALL
  SELECT '인프라','Terraform' UNION ALL SELECT '인프라','CI/CD' UNION ALL SELECT '인프라','모니터링' UNION ALL
  SELECT '인프라','Prometheus' UNION ALL SELECT '인프라','Grafana' UNION ALL SELECT '인프라','무중단 배포' UNION ALL SELECT '인프라','서비스 메시' UNION ALL
  SELECT '데이터 엔지니어링','Kafka' UNION ALL SELECT '데이터 엔지니어링','Spark' UNION ALL SELECT '데이터 엔지니어링','Airflow' UNION ALL
  SELECT '데이터 엔지니어링','데이터 파이프라인' UNION ALL SELECT '데이터 엔지니어링','ETL' UNION ALL SELECT '데이터 엔지니어링','데이터 레이크' UNION ALL
  SELECT '데이터 엔지니어링','BigQuery' UNION ALL SELECT '데이터 엔지니어링','스트리밍' UNION ALL SELECT '데이터 엔지니어링','CDC' UNION ALL SELECT '데이터 엔지니어링','dbt' UNION ALL
  SELECT 'AI','머신러닝' UNION ALL SELECT 'AI','딥러닝' UNION ALL SELECT 'AI','LLM' UNION ALL
  SELECT 'AI','생성형 AI' UNION ALL SELECT 'AI','AI 에이전트' UNION ALL SELECT 'AI','추천 시스템' UNION ALL
  SELECT 'AI','MLOps' UNION ALL SELECT 'AI','임베딩' UNION ALL SELECT 'AI','파인튜닝' UNION ALL SELECT 'AI','벡터 DB' UNION ALL SELECT 'AI','PyTorch' UNION ALL
  SELECT '모바일','Android' UNION ALL SELECT '모바일','iOS' UNION ALL SELECT '모바일','Swift' UNION ALL
  SELECT '모바일','SwiftUI' UNION ALL SELECT '모바일','Jetpack Compose' UNION ALL SELECT '모바일','Flutter' UNION ALL
  SELECT '모바일','React Native' UNION ALL SELECT '모바일','Kotlin' UNION ALL SELECT '모바일','앱 성능' UNION ALL
  SELECT '아키텍처','MSA' UNION ALL SELECT '아키텍처','DDD' UNION ALL SELECT '아키텍처','이벤트 기반' UNION ALL
  SELECT '아키텍처','분산 시스템' UNION ALL SELECT '아키텍처','대용량 트래픽' UNION ALL SELECT '아키텍처','성능 최적화' UNION ALL
  SELECT '아키텍처','캐시' UNION ALL SELECT '아키텍처','헥사고날' UNION ALL SELECT '아키텍처','이벤트 소싱' UNION ALL SELECT '아키텍처','CQRS' UNION ALL
  SELECT '테스트','테스트' UNION ALL SELECT '테스트','단위 테스트' UNION ALL SELECT '테스트','통합 테스트' UNION ALL
  SELECT '테스트','E2E 테스트' UNION ALL SELECT '테스트','TDD' UNION ALL SELECT '테스트','테스트 코드' UNION ALL
  SELECT '테스트','테스트 자동화' UNION ALL SELECT '테스트','JUnit' UNION ALL SELECT '테스트','Mock' UNION ALL SELECT '테스트','부하 테스트' UNION ALL
  SELECT '개발 문화','코드 리뷰' UNION ALL SELECT '개발 문화','애자일' UNION ALL SELECT '개발 문화','스크럼' UNION ALL
  SELECT '개발 문화','회고' UNION ALL SELECT '개발 문화','기술 부채' UNION ALL SELECT '개발 문화','리팩터링' UNION ALL
  SELECT '개발 문화','온보딩' UNION ALL SELECT '개발 문화','생산성' UNION ALL SELECT '개발 문화','페어 프로그래밍' UNION ALL SELECT '개발 문화','문서화'
) m JOIN topic t ON t.name = m.topic JOIN keyword k ON k.name = m.keyword;
