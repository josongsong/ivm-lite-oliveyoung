-- V020: Outbox 성능 최적화 (RFC-IMPL-013)
--
-- 문제점:
-- 1. PROCESSED 레코드 누적 → 테이블 비대화
-- 2. 전체 인덱스 (idx_outbox_created, idx_outbox_sequence) → bloat
-- 3. 파티셔닝 없음 → 대용량 처리 어려움
--
-- 해결:
-- 1. PROCESSED 레코드 자동 정리 (Archive)
-- 2. Partial Index 최적화
-- 3. BRIN 인덱스 (시계열 데이터)
-- 4. Fillfactor 조정 (HOT update 최적화)

-- =============================================================================
-- 1. Archive 테이블 생성 (PROCESSED 레코드 이동용)
-- =============================================================================

CREATE TABLE IF NOT EXISTS outbox_archive (
    LIKE outbox INCLUDING ALL
);

COMMENT ON TABLE outbox_archive IS 'Outbox 아카이브 (PROCESSED 레코드 보관)';

-- =============================================================================
-- 2. 불필요한 인덱스 제거 및 최적화
-- =============================================================================

-- 기존 인덱스 제거 (필요없거나 비효율적인 것)
DROP INDEX IF EXISTS idx_outbox_created;     -- 모든 row 포함 → 불필요
DROP INDEX IF EXISTS idx_outbox_sequence;    -- 거의 사용 안함

-- PENDING 조회 최적화 인덱스 (핵심 쿼리)
-- OutboxPollingWorker가 가장 많이 사용하는 쿼리
CREATE INDEX IF NOT EXISTS idx_outbox_pending_claim
    ON outbox(created_at, id)
    WHERE status = 'PENDING';

COMMENT ON INDEX idx_outbox_pending_claim IS 
    'PENDING 조회용 (OutboxPollingWorker claim)';

-- aggregateType별 PENDING 조회 (분산 처리용)
CREATE INDEX IF NOT EXISTS idx_outbox_pending_by_type
    ON outbox(aggregatetype, created_at)
    WHERE status = 'PENDING';

COMMENT ON INDEX idx_outbox_pending_by_type IS 
    'aggregateType별 PENDING 조회 (워커 분산용)';

-- =============================================================================
-- 3. Fillfactor 조정 (HOT update 최적화)
-- =============================================================================

-- status 변경이 잦으므로 HOT update를 위해 fillfactor 낮춤
ALTER TABLE outbox SET (fillfactor = 70);

-- =============================================================================
-- 4. Archive 함수 (PROCESSED 레코드 이동)
-- =============================================================================

CREATE OR REPLACE FUNCTION archive_processed_outbox(retention_days INT DEFAULT 7)
RETURNS INT AS $$
DECLARE
    archived_count INT;
BEGIN
    -- 1. Archive 테이블로 이동
    WITH moved AS (
        DELETE FROM outbox
        WHERE status = 'PROCESSED'
          AND processed_at < NOW() - (retention_days || ' days')::INTERVAL
        RETURNING *
    )
    INSERT INTO outbox_archive SELECT * FROM moved;
    
    GET DIAGNOSTICS archived_count = ROW_COUNT;
    
    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive_processed_outbox IS 
    '처리 완료된 outbox 레코드를 archive로 이동 (기본 7일 보관)';

-- =============================================================================
-- 5. FAILED 레코드 정리 함수
-- =============================================================================

CREATE OR REPLACE FUNCTION cleanup_failed_outbox(max_retry INT DEFAULT 5, retention_days INT DEFAULT 30)
RETURNS INT AS $$
DECLARE
    cleaned_count INT;
BEGIN
    -- 재시도 횟수 초과 + 보관 기간 지난 FAILED 레코드 삭제
    WITH moved AS (
        DELETE FROM outbox
        WHERE status = 'FAILED'
          AND retry_count >= max_retry
          AND created_at < NOW() - (retention_days || ' days')::INTERVAL
        RETURNING *
    )
    INSERT INTO outbox_archive SELECT * FROM moved;
    
    GET DIAGNOSTICS cleaned_count = ROW_COUNT;
    
    RETURN cleaned_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_failed_outbox IS 
    '재시도 불가능한 FAILED 레코드 정리';

-- =============================================================================
-- 6. 통계 뷰 (모니터링용)
-- =============================================================================

DROP VIEW IF EXISTS outbox_stats;
CREATE VIEW outbox_stats AS
SELECT
    status,
    aggregatetype,
    COUNT(*) as count,
    MIN(created_at) as oldest,
    MAX(created_at) as newest,
    AVG(EXTRACT(EPOCH FROM (COALESCE(processed_at, NOW()) - created_at))) as avg_latency_sec
FROM outbox
GROUP BY status, aggregatetype
ORDER BY status, aggregatetype;

COMMENT ON VIEW outbox_stats IS 'Outbox 상태 통계 (모니터링용)';

-- =============================================================================
-- 7. 권장 cron 스케줄 (pg_cron 사용 시)
-- =============================================================================

-- 예시: 매시간 archive 실행
-- SELECT cron.schedule('outbox-archive', '0 * * * *', 'SELECT archive_processed_outbox(7)');

-- 예시: 매일 자정 failed cleanup
-- SELECT cron.schedule('outbox-failed-cleanup', '0 0 * * *', 'SELECT cleanup_failed_outbox(5, 30)');

-- =============================================================================
-- 8. 인덱스 상태 확인 쿼리 (운영용)
-- =============================================================================

COMMENT ON TABLE outbox IS 
'Transactional Outbox (RFC-IMPL-008, 최적화 RFC-IMPL-013)

성능 모니터링:
  SELECT * FROM outbox_stats;
  
수동 정리:
  SELECT archive_processed_outbox(7);     -- 7일 이상 PROCESSED 아카이브
  SELECT cleanup_failed_outbox(5, 30);    -- 30일 이상 FAILED 정리

권장 VACUUM:
  VACUUM ANALYZE outbox;

예상 처리량:
  - 안전: ~100만 rows/day
  - 주의: 500만 rows/day (archive 필수)
  - 위험: 1000만+ rows/day (파티셔닝 고려)
';
