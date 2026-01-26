-- V004: Inverted Index 테이블
-- Slice 검색용 역인덱스 (RFC-IMPL-006, v1.1+)

CREATE TABLE inverted_index (
    -- PK
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- 인덱스 키
    tenant_id       VARCHAR(64) NOT NULL,
    index_key       VARCHAR(512) NOT NULL,  -- 복합 키: slice_type:field_path:value
    
    -- 참조 대상
    entity_key      VARCHAR(256) NOT NULL,
    slice_version   BIGINT NOT NULL,
    
    -- 메타데이터
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 유니크 제약
    CONSTRAINT inverted_index_unique_key UNIQUE (tenant_id, index_key, entity_key)
);

-- 인덱스 (조회 성능)
CREATE INDEX idx_inverted_tenant_key ON inverted_index(tenant_id, index_key);
CREATE INDEX idx_inverted_entity ON inverted_index(tenant_id, entity_key);

-- 코멘트
COMMENT ON TABLE inverted_index IS 'Slice 검색용 역인덱스 (RFC-IMPL-006)';
COMMENT ON COLUMN inverted_index.index_key IS '복합 인덱스 키 (slice_type:path:value)';
