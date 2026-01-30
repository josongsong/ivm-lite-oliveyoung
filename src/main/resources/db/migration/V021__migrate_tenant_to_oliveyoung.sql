-- V021: 모든 tenant를 oliveyoung으로 통일
-- mecca, playground 등 기존 tenant 데이터를 oliveyoung으로 마이그레이션
-- 이미 수동으로 실행됨 (2026-01-30)

-- raw_data 테이블 업데이트
UPDATE raw_data SET tenant_id = 'oliveyoung' WHERE tenant_id <> 'oliveyoung';

-- slices 테이블 업데이트
UPDATE slices SET tenant_id = 'oliveyoung' WHERE tenant_id <> 'oliveyoung';

-- inverted_index 테이블 업데이트
UPDATE inverted_index SET tenant_id = 'oliveyoung' WHERE tenant_id <> 'oliveyoung';
