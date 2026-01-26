RFC-IMPL-004 — Slicing Workflow v1 (FULL) + Slice Idempotency + InvertedIndex Writes

Status: Accepted
Created: 2026-01-25
Scope: FULL slicing을 “동작 가능한 최소”로 구현하기 위한 실행 플랜/완료 기준
Depends on: RFC-V4-001, RFC-V4-002, RFC-V4-006, RFC-IMPL-002, RFC-IMPL-003
Audience: Runtime Developers
Non-Goals: INCREMENTAL slicing(ChangeSet 기반), compiler optimization(RFC-009), sink(v1.1+)

0. Executive Summary

본 RFC는 FULL slicing 경로를 구현 가능한 수준으로 고정한다.
v1에서는 “CORE slice 1개 생성”부터 시작하되, 결정성/멱등성 불변식은 반드시 강제한다.

---

1. Workflow Definition (SSOT)

SlicingWorkflow.execute(tenantId, entityKey, version)

Steps:
1) RawDataRepositoryPort.get(tenantId, entityKey, version) (fail-closed)
2) (v1) ruleSet은 파라미터로 받지 않고 v1 고정값 사용
3) SliceRecord 생성 (sliceType=CORE, ruleSetId/ruleSetVersion 고정)
4) SliceRepositoryPort.putAllIdempotent([slice])
5) InvertedIndex는 v1 범위 밖 (join/팬아웃은 v1.1+)

Output:
- Ok(sliceKeys)
- Err(DomainError)

---

2. Slice Idempotency Rules (Must)

- 동일 (tenantId, entityKey, version, sliceType) put:
  - 동일 hash면 OK
  - hash 다르면 invariant violation (Err)

---

3. Join / InvertedIndex Rules (v1)

- v1에서는 join/팬아웃/InvertedIndex write는 범위 밖으로 둔다.
- 단, JoinSpecContract / InvertedIndexContract를 “로드 가능”하게 하는 준비는 RFC-IMPL-002에서 수행한다.

---

4. Tests (Must)

- 같은 입력으로 slicing 2회 → 동일 slice_hash + idempotent OK
- 같은 key/version/sliceType 다른 payload 시도 → Err

