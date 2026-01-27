-- V010: Outbox 멱등성 및 실패 추적 컬럼 추가
-- RFC-IMPL Phase B-2 강화: 결정적 idempotencyKey, failureReason 기록
--
-- 문제 해결:
-- 1. 동일 비즈니스 이벤트 중복 방지 (idempotency_key)
-- 2. 실패 원인 추적 (failure_reason)
-- 3. Race condition 방지 (UNIQUE 제약조건)

-- 1. idempotency_key 컬럼 추가 (결정적 해시 기반)
-- 동일 aggregateid + type + payload → 동일 key
ALTER TABLE outbox ADD COLUMN idempotency_key VARCHAR(64);

-- 기존 데이터에 대해 idempotency_key 생성 (마이그레이션 호환)
-- MD5 해시 사용 (PostgreSQL 내장)
UPDATE outbox 
SET idempotency_key = 'idem_' || MD5(aggregateid || '|' || type || '|' || payload::text)::VARCHAR(32)
WHERE idempotency_key IS NULL;

-- NOT NULL 제약조건 추가
ALTER TABLE outbox ALTER COLUMN idempotency_key SET NOT NULL;

-- UNIQUE 인덱스 (중복 이벤트 방지)
CREATE UNIQUE INDEX idx_outbox_idempotency_key ON outbox(idempotency_key);

-- 2. failure_reason 컬럼 추가 (실패 원인 추적)
ALTER TABLE outbox ADD COLUMN failure_reason TEXT;

-- 3. 조회 성능 인덱스 (SELECT FOR UPDATE SKIP LOCKED 최적화)
-- status가 PENDING인 행만 인덱싱
DROP INDEX IF EXISTS idx_outbox_status_created;
CREATE INDEX idx_outbox_pending_fifo ON outbox(created_at ASC)
    WHERE status = 'PENDING';

-- 코멘트
COMMENT ON COLUMN outbox.idempotency_key IS '멱등성 키: 동일 이벤트 중복 방지 (sha256 기반)';
COMMENT ON COLUMN outbox.failure_reason IS '실패 원인: FAILED 상태일 때 에러 메시지';
COMMENT ON INDEX idx_outbox_idempotency_key IS '멱등성 보장: 동일 이벤트 중복 INSERT 방지';
COMMENT ON INDEX idx_outbox_pending_fifo IS 'Polling 최적화: PENDING 상태만 FIFO 조회';
