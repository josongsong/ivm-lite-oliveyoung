-- V008: Slice Tombstone 지원 (소프트 삭제)
--
-- RFC-IMPL: Slice 삭제 시 물리적 삭제 대신 tombstone 마킹
-- 이점: 히스토리 추적, 복구 가능, CDC 이벤트 발행 가능

-- 1. Tombstone 컬럼 추가
ALTER TABLE slices
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS deleted_at_version BIGINT,
    ADD COLUMN IF NOT EXISTS delete_reason VARCHAR(32);

-- 2. Tombstone 인덱스 (삭제된 slice 조회용)
CREATE INDEX IF NOT EXISTS idx_slices_tombstone
    ON slices(tenant_id, is_deleted)
    WHERE is_deleted = true;

-- 3. 일관성 제약 조건 (멱등성)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_tombstone_consistency'
        AND conrelid = 'slices'::regclass
    ) THEN
        ALTER TABLE slices
            ADD CONSTRAINT chk_tombstone_consistency CHECK (
                (is_deleted = false AND deleted_at_version IS NULL AND delete_reason IS NULL)
                OR
                (is_deleted = true AND deleted_at_version IS NOT NULL AND delete_reason IS NOT NULL)
            );
    END IF;
END $$;

-- 4. 코멘트
COMMENT ON COLUMN slices.is_deleted IS 'Tombstone 여부 (true면 삭제됨)';
COMMENT ON COLUMN slices.deleted_at_version IS '삭제 시점의 slice_version';
COMMENT ON COLUMN slices.delete_reason IS '삭제 사유: EXPLICIT, SUPERSEDED, TTL_EXPIRED';
