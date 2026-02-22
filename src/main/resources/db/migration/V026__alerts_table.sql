-- V026: Alerts 테이블 (모니터링 알림)
-- AlertEngine이 발생시킨 Alert의 영구 저장

CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY,
    rule_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    context JSONB NOT NULL DEFAULT '{}',
    fired_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    silenced_until TIMESTAMPTZ,
    occurrences INT NOT NULL DEFAULT 1,
    labels JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_rule ON alerts(rule_id, status);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity, status);
CREATE INDEX IF NOT EXISTS idx_alerts_fired ON alerts(fired_at DESC);
