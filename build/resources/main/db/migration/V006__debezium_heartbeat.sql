-- V006: Debezium Heartbeat 테이블
-- WAL 위치 추적용 (긴 시간 변경 없을 때)

CREATE TABLE debezium_heartbeat (
    id              INT PRIMARY KEY DEFAULT 1,
    last_heartbeat  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- 단일 행만 허용
    CONSTRAINT debezium_heartbeat_single_row CHECK (id = 1)
);

-- 초기 데이터
INSERT INTO debezium_heartbeat (id, last_heartbeat) VALUES (1, NOW());

-- 코멘트
COMMENT ON TABLE debezium_heartbeat IS 'Debezium CDC heartbeat (WAL 위치 추적)';
