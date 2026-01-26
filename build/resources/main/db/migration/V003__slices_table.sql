-- V003: Slices 저장 테이블
-- 컴파일된 Slice 레코드 (RFC-IMPL-004)

CREATE TABLE slices (
    -- PK
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- 비즈니스 키
    tenant_id       VARCHAR(64) NOT NULL,
    entity_key      VARCHAR(256) NOT NULL,
    slice_type      VARCHAR(64) NOT NULL,   -- 예: CORE, PRICING, INVENTORY
    slice_version   BIGINT NOT NULL,
    
    -- 컨텐츠
    content_hash    VARCHAR(64) NOT NULL,   -- SHA256 hex
    content         JSONB NOT NULL,
    
    -- 소스 추적
    source_raw_id   UUID REFERENCES raw_data(id),
    ruleset_ref     VARCHAR(128),           -- 예: ruleset.core.v1@1.0.0
    
    -- 메타데이터
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 멱등성 제약
    CONSTRAINT slices_idempotent_key UNIQUE (tenant_id, entity_key, slice_type, slice_version)
);

-- 인덱스
CREATE INDEX idx_slices_tenant_entity ON slices(tenant_id, entity_key);
CREATE INDEX idx_slices_type ON slices(slice_type);
CREATE INDEX idx_slices_source ON slices(source_raw_id);
CREATE INDEX idx_slices_created ON slices(created_at);

-- 코멘트
COMMENT ON TABLE slices IS 'Slice 레코드 저장소 (RFC-IMPL-004)';
COMMENT ON COLUMN slices.slice_type IS 'Slice 타입 (CORE, PRICING 등)';
COMMENT ON COLUMN slices.source_raw_id IS '원본 RawData ID (추적용)';
COMMENT ON COLUMN slices.ruleset_ref IS '적용된 RuleSet 참조';
