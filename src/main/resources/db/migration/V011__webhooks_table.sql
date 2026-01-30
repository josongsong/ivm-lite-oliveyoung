-- V011: Webhook & Event Hooks 테이블
-- 파이프라인 이벤트를 외부 시스템에 HTTP로 전송하기 위한 스키마

-- webhooks: 웹훅 정의
CREATE TABLE webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    url             TEXT NOT NULL,
    events          TEXT[] NOT NULL,              -- {'SLICE_CREATED', 'VIEW_CHANGED'}
    filters         JSONB DEFAULT '{}',           -- {"entityType": "PRODUCT", "tenantId": "oliveyoung"}
    headers         JSONB DEFAULT '{}',           -- {"Authorization": "Bearer xxx"}
    payload_template TEXT,                        -- 커스텀 페이로드 템플릿 (Mustache/Handlebars 문법)
    is_active       BOOLEAN DEFAULT true,
    retry_policy    JSONB DEFAULT '{"maxRetries": 5, "initialDelayMs": 1000, "maxDelayMs": 60000, "multiplier": 2.0}',
    secret_token    VARCHAR(256),                 -- HMAC-SHA256 서명용 시크릿
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- webhook_deliveries: 전송 기록 (감사 로그)
CREATE TABLE webhook_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    event_payload   JSONB NOT NULL,
    request_headers JSONB,
    request_body    TEXT,
    response_status INT,
    response_body   TEXT,
    response_headers JSONB,
    latency_ms      INT,
    status          VARCHAR(20) NOT NULL,         -- PENDING, SUCCESS, FAILED, RETRYING
    error_message   TEXT,
    attempt_count   INT DEFAULT 1,
    next_retry_at   TIMESTAMPTZ,                  -- 다음 재시도 예정 시각
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_webhooks_is_active ON webhooks(is_active) WHERE is_active = true;
CREATE INDEX idx_webhooks_events ON webhooks USING GIN(events);

CREATE INDEX idx_webhook_deliveries_webhook_id ON webhook_deliveries(webhook_id);
CREATE INDEX idx_webhook_deliveries_status ON webhook_deliveries(status);
CREATE INDEX idx_webhook_deliveries_created_at ON webhook_deliveries(created_at DESC);
CREATE INDEX idx_webhook_deliveries_next_retry ON webhook_deliveries(next_retry_at) WHERE status = 'RETRYING';

-- 트리거: updated_at 자동 갱신
CREATE OR REPLACE FUNCTION update_webhooks_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_webhooks_updated_at
    BEFORE UPDATE ON webhooks
    FOR EACH ROW
    EXECUTE FUNCTION update_webhooks_updated_at();

-- 코멘트
COMMENT ON TABLE webhooks IS '웹훅 정의 - 파이프라인 이벤트를 외부 시스템에 HTTP로 전송';
COMMENT ON COLUMN webhooks.events IS '구독할 이벤트 타입 배열 (RAWDATA_INGESTED, SLICE_CREATED 등)';
COMMENT ON COLUMN webhooks.filters IS '이벤트 필터링 조건 (entityType, tenantId 등)';
COMMENT ON COLUMN webhooks.headers IS '요청 시 추가할 커스텀 HTTP 헤더';
COMMENT ON COLUMN webhooks.payload_template IS '커스텀 페이로드 템플릿 (null이면 기본 포맷 사용)';
COMMENT ON COLUMN webhooks.retry_policy IS '재시도 정책 (지수 백오프 설정)';
COMMENT ON COLUMN webhooks.secret_token IS 'HMAC-SHA256 서명용 시크릿 토큰';

COMMENT ON TABLE webhook_deliveries IS '웹훅 전송 기록 - 감사 로그 및 재시도 관리';
COMMENT ON COLUMN webhook_deliveries.status IS 'PENDING=대기, SUCCESS=성공, FAILED=실패, RETRYING=재시도중';
COMMENT ON COLUMN webhook_deliveries.next_retry_at IS '다음 재시도 예정 시각 (지수 백오프 계산)';
