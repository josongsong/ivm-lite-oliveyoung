-- V002: RawData 저장 테이블
-- 멱등성: (tenant_id, entity_key, version) 유니크

CREATE TABLE raw_data (
    -- PK: UUID (jOOQ에서 타입 안전하게 사용)
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- 비즈니스 키
    tenant_id       VARCHAR(64) NOT NULL,
    entity_key      VARCHAR(256) NOT NULL,
    version         BIGINT NOT NULL,
    
    -- 스키마 정보 (RFC-IMPL-003: 멱등성에 포함)
    schema_id       VARCHAR(128) NOT NULL,
    schema_version  VARCHAR(32) NOT NULL,
    
    -- 컨텐츠
    content_hash    VARCHAR(64) NOT NULL,  -- SHA256 hex (결정성 검증)
    content         JSONB NOT NULL,
    
    -- 메타데이터
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 멱등성 제약: 같은 (tenant, entity, version)은 한 번만
    CONSTRAINT raw_data_idempotent_key UNIQUE (tenant_id, entity_key, version)
);

-- 인덱스
CREATE INDEX idx_raw_data_tenant_entity ON raw_data(tenant_id, entity_key);
CREATE INDEX idx_raw_data_schema ON raw_data(schema_id, schema_version);
CREATE INDEX idx_raw_data_created ON raw_data(created_at);

-- 코멘트 (jOOQ 코드에도 반영됨)
COMMENT ON TABLE raw_data IS 'RawData 레코드 저장소 (RFC-IMPL-003)';
COMMENT ON COLUMN raw_data.id IS 'UUID PK';
COMMENT ON COLUMN raw_data.tenant_id IS '테넌트 식별자';
COMMENT ON COLUMN raw_data.entity_key IS '엔티티 키 (예: product-123)';
COMMENT ON COLUMN raw_data.version IS '버전 (monotonic increasing)';
COMMENT ON COLUMN raw_data.schema_id IS '스키마 ID (예: product.v1)';
COMMENT ON COLUMN raw_data.schema_version IS '스키마 버전 (SemVer)';
COMMENT ON COLUMN raw_data.content_hash IS 'SHA256(canonicalized content)';
COMMENT ON COLUMN raw_data.content IS 'JSON 컨텐츠';
COMMENT ON COLUMN raw_data.created_at IS '생성 시각';
