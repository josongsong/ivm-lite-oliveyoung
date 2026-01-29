-- V009: Inverted Index v2 (유연한 인덱싱)
--
-- RFC-IMPL: 참조 관계 및 유연한 검색을 위한 컬럼 추가
-- 기존 index_key 방식에서 type+value 방식으로 전환

-- 1. 신규 컬럼 추가
ALTER TABLE inverted_index
    ADD COLUMN IF NOT EXISTS index_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS index_value VARCHAR(512),
    ADD COLUMN IF NOT EXISTS ref_version BIGINT,
    ADD COLUMN IF NOT EXISTS target_entity_key VARCHAR(256),
    ADD COLUMN IF NOT EXISTS target_version BIGINT,
    ADD COLUMN IF NOT EXISTS slice_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS slice_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_tombstone BOOLEAN DEFAULT false;

-- 2. 새 유니크 제약 조건 (멱등성)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'inverted_index_unique_key_v2'
        AND conrelid = 'inverted_index'::regclass
    ) THEN
        ALTER TABLE inverted_index
            ADD CONSTRAINT inverted_index_unique_key_v2
            UNIQUE (tenant_id, index_type, index_value, entity_key, slice_version);
    END IF;
END $$;

-- 3. 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_inverted_type_value
    ON inverted_index(tenant_id, index_type, index_value);

-- 4. 코멘트
COMMENT ON COLUMN inverted_index.index_type IS '인덱스 유형: ATTRIBUTE, REFERENCE, COMPUTED';
COMMENT ON COLUMN inverted_index.index_value IS '인덱스 값';
COMMENT ON COLUMN inverted_index.target_entity_key IS '참조 대상 엔티티 키 (REFERENCE 타입용)';
COMMENT ON COLUMN inverted_index.target_version IS '참조 대상 버전';
COMMENT ON COLUMN inverted_index.is_tombstone IS 'Tombstone 여부';
