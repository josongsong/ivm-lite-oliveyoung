-- V030: changesets 테이블 제거
-- ChangeSet 저장 미사용 (save() 호출 없음)
-- production에서 InMemoryChangeSetRepository 사용

DROP TABLE IF EXISTS changesets;
