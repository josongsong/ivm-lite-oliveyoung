# ADR-0019: Sink Reliability & Data Consistency

**Status**: Proposed  
**Date**: 2026-02-16  
**Deciders**: Architecture Team  
**RFC**: RFC-020

---

## Context

DynamoDB Streams → Lambda → SinkPlugin 직접 실행 구조에서 CDC best practice 관점 리스크:

- R1: REMOVE 이벤트 무시 → Sink에 삭제된 데이터 잔존
- R2: 버전 충돌 무방비 → 구버전이 신버전 덮어쓸 수 있음
- R3: 실패 레코드 유실 → 재처리 불가
- R4: SinkLedger 미사용 → 멱등성 검증 부재
- R5: SinkEvent 상태 미갱신 → Admin UI에서 처리 여부 불명

## Decision

**Sink Reliability & Data Consistency** 개선을 채택합니다.

### R1: REMOVE 이벤트 처리

- DynamoDB REMOVE → Sink DELETE
- SinkPlugin에 `supportsDelete`, `delete(tenantId, entityKey)` 확장
- OpenSearch: `DELETE /{index}/_doc/{docId}`, S3: `DeleteObject(key)`

### R2: 버전 충돌 방지

- **OpenSearch**: External Versioning (`version_type: external`)
  - incoming version > current → 반영
  - incoming <= current → 409 Conflict (무시)
- **S3**: 키에 버전 포함 `v{entityVersion}.json`

### R3: 실패 레코드 관리

- Lambda N회 시도 후 실패 → DynamoDB `ivm-sink-failures` 테이블 저장
- Admin UI: 목록 조회, 재처리 트리거
- TTL 30일

### R4/R5: SinkLedger, 상태 갱신

- SinkLedger Port 구현 (DynamoDB)
- SinkEvent 상태 갱신 (PENDING → SHIPPED/FAILED)

## Consequences

### Positive

- ✅ eventual consistency 보장
- ✅ 실패 복원력
- ✅ 순서 안전성 (구버전 덮어쓰기 방지)
- ✅ 운영 가시성

### Negative

- ⚠️ SinkPlugin 인터페이스 확장
- ⚠️ 실패 테이블 운영

---

## 참고

- [RFC-020](../rfc_archive/2026-02/RFC-020-sink-reliability-and-consistency.md)
