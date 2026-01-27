-- ivm-lite PostgreSQL Bootstrap (docker-compose 초기화용)
-- 
-- ⚠️ 주의: 실제 스키마는 Flyway 마이그레이션으로 관리됩니다!
-- 이 파일은 docker-compose로 PostgreSQL 컨테이너 시작 시 최소 설정만 합니다.
-- 
-- 실제 테이블 정의: src/main/resources/db/migration/V*.sql
-- 
-- 사용법:
--   1. docker-compose up -d postgres
--   2. ./gradlew flywayMigrate  (Flyway가 테이블 생성)
--   3. ./gradlew jooqCodegen    (jOOQ가 Kotlin 코드 생성)

-- Extensions만 미리 설치 (Flyway 마이그레이션에서 필요)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- WAL 레벨 확인 (Debezium CDC에 필요 - docker-compose에서 설정됨)
-- SHOW wal_level;  -- 'logical' 이어야 함

-- 이후 테이블은 Flyway 마이그레이션에서 생성됨:
-- - raw_data (V002)
-- - slices (V003)  
-- - inverted_index (V004)
-- - outbox (V005)
-- - debezium_heartbeat (V006)
-- - outbox 컬럼 추가: status, processed_at, retry_count (V007)
-- - slice 컬럼 추가: tombstone (V008)
-- - inverted_index 개선 (V009)
-- - outbox 컬럼 추가: idempotency_key, failure_reason (V010)
