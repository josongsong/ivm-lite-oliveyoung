-- V025: ChangeSet 테이블 (변경 추적)
-- ImpactMap 기반 변경 감지 결과를 영구 저장

CREATE TABLE IF NOT EXISTS changesets (
    changeset_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    from_version BIGINT NOT NULL,
    to_version BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    changed_paths JSONB NOT NULL DEFAULT '[]',
    impacted_slice_types TEXT[] NOT NULL DEFAULT '{}',
    impact_map JSONB NOT NULL DEFAULT '{}',
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_changesets_entity ON changesets(tenant_id, entity_key);
CREATE INDEX IF NOT EXISTS idx_changesets_version ON changesets(tenant_id, entity_key, to_version DESC);
CREATE INDEX IF NOT EXISTS idx_changesets_type ON changesets(tenant_id, change_type, created_at DESC);
