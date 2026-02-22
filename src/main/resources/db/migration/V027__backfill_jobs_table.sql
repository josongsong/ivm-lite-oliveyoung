-- V027: BackfillJobs 테이블 (재처리 작업)
-- Backfill 작업의 전체 라이프사이클을 영구 저장

CREATE TABLE IF NOT EXISTS backfill_jobs (
    id UUID PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    type VARCHAR(100) NOT NULL,
    scope JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(50) NOT NULL,
    priority INT NOT NULL DEFAULT 5,
    config JSONB NOT NULL DEFAULT '{}',
    progress JSONB NOT NULL DEFAULT '{}',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    dry_run_result JSONB
);

CREATE INDEX IF NOT EXISTS idx_backfill_status ON backfill_jobs(status);
CREATE INDEX IF NOT EXISTS idx_backfill_type ON backfill_jobs(type, status);
CREATE INDEX IF NOT EXISTS idx_backfill_priority ON backfill_jobs(priority) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_backfill_created ON backfill_jobs(created_at DESC);
