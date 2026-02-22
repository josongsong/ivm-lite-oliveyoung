-- V022: Views 테이블 생성 (RFC-003 ViewDefinition)
--
-- View: Slice 조합으로 생성된 최종 데이터
-- - RawData → Slicing → View Composition
-- - 멱등성: (tenant_id, entity_key, version, view_type) UNIQUE
-- - hash 기반 중복 방지

CREATE TABLE IF NOT EXISTS views (
    -- 복합 키
    tenant_id VARCHAR(255) NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    view_type VARCHAR(100) NOT NULL,          -- 예: PRODUCT_DETAIL, PRODUCT_SEARCH

    -- View 데이터
    data JSONB NOT NULL,                      -- 조합된 View JSON
    hash VARCHAR(64) NOT NULL,                -- SHA-256 hash (멱등성 키)

    -- 메타데이터
    view_def_id VARCHAR(255) NOT NULL,        -- ViewDefinition ID
    view_def_version VARCHAR(50) NOT NULL,    -- ViewDefinition 버전
    used_slices TEXT[] NOT NULL,              -- 사용된 SliceType 배열

    -- 타임스탬프
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 기본키: 복합 키
    PRIMARY KEY (tenant_id, entity_key, version, view_type)
);

-- 인덱스: hash 기반 중복 체크
CREATE INDEX idx_views_hash ON views(hash);

-- 인덱스: 최신 버전 조회 (tenant + entity + view_type)
CREATE INDEX idx_views_latest ON views(tenant_id, entity_key, view_type, version DESC);

-- 인덱스: ViewDefinition 버전별 조회
CREATE INDEX idx_views_viewdef ON views(view_def_id, view_def_version);

-- 인덱스: created_at (시간 기반 조회/클린업)
CREATE INDEX idx_views_created_at ON views(created_at DESC);

-- 코멘트
COMMENT ON TABLE views IS 'RFC-003: View records (Slice 조합 결과)';
COMMENT ON COLUMN views.tenant_id IS '테넌트 ID';
COMMENT ON COLUMN views.entity_key IS '엔티티 키';
COMMENT ON COLUMN views.version IS '데이터 버전 (RawData 버전과 동일)';
COMMENT ON COLUMN views.view_type IS 'View 타입 (예: PRODUCT_DETAIL)';
COMMENT ON COLUMN views.data IS '조합된 View 데이터 (JSON)';
COMMENT ON COLUMN views.hash IS 'SHA-256 hash (멱등성 키)';
COMMENT ON COLUMN views.view_def_id IS 'ViewDefinition ID';
COMMENT ON COLUMN views.view_def_version IS 'ViewDefinition 버전';
COMMENT ON COLUMN views.used_slices IS '사용된 SliceType 배열';
COMMENT ON COLUMN views.created_at IS '생성 시각';
