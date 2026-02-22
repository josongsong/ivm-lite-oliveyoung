-- V031: debezium_heartbeat 테이블 제거
-- PostgreSQL Outbox → DynamoDB Streams 전환 완료
-- Debezium CDC 미사용

DROP TABLE IF EXISTS debezium_heartbeat;
