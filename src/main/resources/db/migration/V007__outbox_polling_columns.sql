-- V007: Outbox claim 지원 (PROCESSING 상태 + 타임아웃 복구)
-- 
-- 문제점: 기존 findPending()은 트랜잭션 종료 시 락 해제 → 중복 처리 가능
-- 해결: PENDING → PROCESSING 원자적 전환 + stale 복구
--
-- 상태 흐름: PENDING → PROCESSING → PROCESSED/FAILED
--                        ↓ (timeout)
--                     PENDING (재시도)

-- 1. 신규 컬럼 추가
ALTER TABLE outbox 
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT;

-- 2. 기존 데이터 마이그레이션 (이미 있는 row는 PROCESSED로 간주)
UPDATE outbox 
SET status = 'PROCESSED', 
    processed_at = created_at
WHERE status = 'PENDING' 
  AND created_at < NOW() - INTERVAL '1 day';

-- 3. 인덱스 추가
-- PENDING/PROCESSING 조회용 (claim, stale recovery)
CREATE INDEX IF NOT EXISTS idx_outbox_status_created 
    ON outbox(status, created_at) 
    WHERE status IN ('PENDING', 'PROCESSING');

-- Stale 복구용 (PROCESSING + claimed_at)
CREATE INDEX IF NOT EXISTS idx_outbox_stale_processing 
    ON outbox(claimed_at) 
    WHERE status = 'PROCESSING';

-- Idempotency key 유니크 제약 (중복 이벤트 방지)
CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_idempotency_key 
    ON outbox(idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

-- 4. 코멘트
COMMENT ON COLUMN outbox.idempotency_key IS '멱등성 키 (동일 비즈니스 이벤트 중복 방지)';
COMMENT ON COLUMN outbox.status IS '처리 상태: PENDING, PROCESSING, PROCESSED, FAILED';
COMMENT ON COLUMN outbox.claimed_at IS 'Worker가 claim한 시각 (PROCESSING 전환 시)';
COMMENT ON COLUMN outbox.claimed_by IS 'Claim한 worker ID (디버깅용)';
COMMENT ON COLUMN outbox.processed_at IS '처리 완료 시각';
COMMENT ON COLUMN outbox.retry_count IS '재시도 횟수 (최대 5회)';
COMMENT ON COLUMN outbox.failure_reason IS '실패 사유';
