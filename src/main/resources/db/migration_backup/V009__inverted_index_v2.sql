-- V009: Inverted Index 테이블 확장 (RFC-IMPL-010 GAP-E)
-- 도메인 모델에 맞게 컬럼 추가

-- 새 컬럼 추가 (기존 데이터와 호환성 유지)
ALTER TABLE inverted_index
    ADD COLUMN IF NOT EXISTS index_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS index_value VARCHAR(512),
    ADD COLUMN IF NOT EXISTS ref_version BIGINT,
    ADD COLUMN IF NOT EXISTS target_entity_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS target_version BIGINT,
    ADD COLUMN IF NOT EXISTS slice_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS slice_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN DEFAULT FALSE;

-- 기존 index_key에서 데이터 마이그레이션 (형식: type:value)
UPDATE inverted_index 
SET 
    index_type = split_part(index_key, ':', 1),
    index_value = substring(index_key from position(':' in index_key) + 1),
    ref_version = slice_version,
    target_entity_key = entity_key,
    target_version = slice_version,
    slice_type = 'CORE',
    slice_hash = '',
    is_tombstone = FALSE
WHERE index_type IS NULL;

-- 새 유니크 제약 (기존 제약 제거 후 추가)
ALTER TABLE inverted_index DROP CONSTRAINT IF EXISTS inverted_index_unique_key;
ALTER TABLE inverted_index ADD CONSTRAINT inverted_index_unique_key_v2 
    UNIQUE (tenant_id, index_type, index_value, entity_key, slice_version);

-- 새 인덱스
CREATE INDEX IF NOT EXISTS idx_inverted_type_value ON inverted_index(tenant_id, index_type, index_value);

COMMENT ON COLUMN inverted_index.index_type IS '인덱스 타입 (e.g., brand, category, tag)';
COMMENT ON COLUMN inverted_index.index_value IS '인덱스 값';
COMMENT ON COLUMN inverted_index.ref_version IS '참조 Slice 버전';
COMMENT ON COLUMN inverted_index.target_entity_key IS '대상 Entity 키';
COMMENT ON COLUMN inverted_index.target_version IS '대상 버전';
COMMENT ON COLUMN inverted_index.slice_type IS 'Slice 타입';
COMMENT ON COLUMN inverted_index.slice_hash IS 'Slice 해시';
COMMENT ON COLUMN inverted_index.is_tombstone IS '삭제 마커';
