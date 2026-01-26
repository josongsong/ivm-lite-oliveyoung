-- V005: Outbox 테이블 (Transactional Outbox Pattern)
-- Debezium CDC로 Kafka 이벤트 발행 (RFC-IMPL-008)

CREATE TABLE outbox (
    -- PK
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Debezium Outbox Event Router 표준 필드
    -- https://debezium.io/documentation/reference/transformations/outbox-event-router.html
    aggregatetype   VARCHAR(128) NOT NULL,  -- 토픽 라우팅: raw_data, slice
    aggregateid     VARCHAR(256) NOT NULL,  -- Kafka 메시지 키: tenant:entity
    type            VARCHAR(128) NOT NULL,  -- 이벤트 타입: RawDataIngested, SliceCreated
    payload         JSONB NOT NULL,         -- 이벤트 페이로드
    
    -- 메타데이터
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 순서 보장용 시퀀스 (선택적)
    sequence_num    BIGSERIAL
);

-- 인덱스 (Debezium 조회용)
CREATE INDEX idx_outbox_aggregate ON outbox(aggregatetype, aggregateid);
CREATE INDEX idx_outbox_created ON outbox(created_at);
CREATE INDEX idx_outbox_sequence ON outbox(sequence_num);

-- 코멘트
COMMENT ON TABLE outbox IS 'Transactional Outbox for CDC (RFC-IMPL-008)';
COMMENT ON COLUMN outbox.aggregatetype IS 'Kafka 토픽 라우팅 키 → ivm.events.{aggregatetype}';
COMMENT ON COLUMN outbox.aggregateid IS 'Kafka 메시지 키 (순서 보장)';
COMMENT ON COLUMN outbox.type IS '이벤트 타입 (Kafka 헤더)';
COMMENT ON COLUMN outbox.payload IS '이벤트 페이로드 (JSON)';
