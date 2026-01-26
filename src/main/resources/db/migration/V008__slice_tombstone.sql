-- V008: Slice Tombstone 컬럼 추가
-- RFC-IMPL-010 Phase D-1: 증분 업데이트 시 삭제된 결과 표현

-- tombstone 관련 컬럼 추가
ALTER TABLE slices ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE slices ADD COLUMN deleted_at_version BIGINT;
ALTER TABLE slices ADD COLUMN delete_reason VARCHAR(32);

-- 삭제된 Slice 조회용 인덱스
CREATE INDEX idx_slices_tombstone ON slices(tenant_id, is_deleted) WHERE is_deleted = TRUE;

-- 제약조건: is_deleted=TRUE면 deleted_at_version, delete_reason 필수
ALTER TABLE slices ADD CONSTRAINT chk_tombstone_consistency
    CHECK (
        (is_deleted = FALSE AND deleted_at_version IS NULL AND delete_reason IS NULL)
        OR
        (is_deleted = TRUE AND deleted_at_version IS NOT NULL AND delete_reason IS NOT NULL)
    );

-- 코멘트
COMMENT ON COLUMN slices.is_deleted IS 'Tombstone 여부 (RFC-IMPL-010 D-1)';
COMMENT ON COLUMN slices.deleted_at_version IS '삭제 시점 버전';
COMMENT ON COLUMN slices.delete_reason IS '삭제 사유 (USER_DELETE, POLICY_HIDE, VALIDATION_FAIL, ARCHIVED)';
