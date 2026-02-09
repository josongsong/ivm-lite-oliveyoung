RFC-IMPL-003 — RawData Ingest Workflow (v1) + Idempotent Storage Contract

Status: Accepted
Created: 2026-01-25
Scope: IngestWorkflow 구현 범위/완료 조건 고정
Depends on: RFC-V4-001, RFC-V4-002, RFC-IMPL-001, RFC-IMPL-002
Audience: Runtime Developers
Non-Goals: slicing, changeset, view, dynamodb adapter

0. Executive Summary

본 RFC는 “원천 입력을 결정적/멱등적으로 저장”하는 IngestWorkflow를 구현 가능한 수준으로 고정한다.

---

1. Workflow Definition (SSOT)

1-1. Input
- tenantId, entityKey, version(Long)
- schemaId, schemaVersion(SemVer)
- payloadJson (string)

1-2. Steps (order fixed)
1) payloadJson canonicalize (RFC8785)
2) raw_hash 계산 규칙 적용 (RFC-V4-002)
3) RawDataRecord 생성
4) RawDataRepositoryPort.putIdempotent(record)

1-3. Output
- 성공: Ok(Unit)
- 실패: DomainError (fail-closed)

---

2. Idempotency Rules (Must)

- 동일 (tenantId, entityKey, version)로 put 시:
  - payloadHash 동일 + schemaId 동일 + schemaVersion 동일이면 OK
  - 하나라도 다르면 invariant violation (Err)

---

3. Tests (Must)

3-1. Determinism
- 동일 payloadJson (구조만 같고 whitespace 다름) → 동일 canonical payload + 동일 hash

3-2. Idempotency
- 같은 record 2번 put → Ok
- 같은 key/version 다른 payload → Err

