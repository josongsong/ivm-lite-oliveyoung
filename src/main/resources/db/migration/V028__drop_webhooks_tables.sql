-- V028: webhooks, webhook_deliveries 테이블 제거
--
-- webhook 기능 미사용 (Admin 라우트/모듈 미연결)
-- PostgreSQL 정리

DROP TABLE IF EXISTS webhook_deliveries;
DROP TABLE IF EXISTS webhooks;
