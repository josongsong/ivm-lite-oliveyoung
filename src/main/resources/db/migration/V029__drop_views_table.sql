-- V029: views 테이블 제거
-- View 조합 결과는 SinkEvent payload로 전달되며, 별도 저장 불필요
-- Query API는 Slice 실시간 조합 사용

DROP TABLE IF EXISTS views;
