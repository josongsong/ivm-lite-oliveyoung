-- V001: 필수 Extensions
-- Flyway 마이그레이션: 확장 기능 설치

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

COMMENT ON EXTENSION "uuid-ossp" IS 'UUID 생성 함수 (uuid_generate_v4)';
