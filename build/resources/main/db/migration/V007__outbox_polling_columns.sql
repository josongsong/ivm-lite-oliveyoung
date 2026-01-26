-- V007: Outbox Polling 지원 컬럼 추가 (RFC-IMPL Phase B-2)
-- v1: Polling 방식, v2: Debezium CDC (포트 동일, 어댑터만 교체)

-- status: PENDING, PROCESSED, FAILED
ALTER TABLE outbox ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

-- processed_at: 처리 완료 시각
ALTER TABLE outbox ADD COLUMN processed_at TIMESTAMPTZ NULL;

-- retry_count: 재시도 횟수
ALTER TABLE outbox ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

-- Polling 인덱스 (PENDING 상태만 빠르게 조회)
CREATE INDEX idx_outbox_status_created ON outbox(status, created_at)
    WHERE status = 'PENDING';

-- 코멘트
COMMENT ON COLUMN outbox.status IS 'PENDING, PROCESSED, FAILED (v1 Polling)';
COMMENT ON COLUMN outbox.processed_at IS '처리 완료 시각';
COMMENT ON COLUMN outbox.retry_count IS '재시도 횟수';
