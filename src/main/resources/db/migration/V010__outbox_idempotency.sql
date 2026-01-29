-- V010: Outbox 멱등성 강화
--
-- RFC-IMPL: 중복 이벤트 방지를 위한 NOT NULL 제약 및 유니크 인덱스 강화

-- 1. idempotency_key NOT NULL 제약 (멱등성)
UPDATE outbox SET idempotency_key = 'legacy_' || id::text WHERE idempotency_key IS NULL;

DO $$
BEGIN
    -- 이미 NOT NULL이 아닌 경우에만 변경
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'outbox' AND column_name = 'idempotency_key' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE outbox ALTER COLUMN idempotency_key SET NOT NULL;
    END IF;
END $$;

-- 2. 유니크 인덱스 재생성 (멱등성)
DROP INDEX IF EXISTS idx_outbox_idempotency_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_idempotency_key ON outbox(idempotency_key);

-- 3. 코멘트
COMMENT ON COLUMN outbox.idempotency_key IS '멱등성 키 (필수, 중복 이벤트 방지)';