RFC-IMPL-005 — QueryView Workflow v1 + Response Determinism

Status: Accepted
Created: 2026-01-25
Scope: queryView() “v1 최소 구현”과 완료 조건 고정
Depends on: RFC-V4-001, RFC-V4-002, RFC-IMPL-002, RFC-IMPL-004
Audience: Runtime Developers / SDK Developers
Non-Goals: ViewDefinitionContract(완전한 조인/프로젝션), codegen

0. Executive Summary

본 RFC는 QueryViewWorkflow의 v1 최소 구현을 고정한다.
v1에서는 “요청된 sliceTypes를 batchGet해서 결정적 JSON으로 묶어 반환”한다.

---

1. Workflow Definition (SSOT)

QueryViewWorkflow.execute(tenantId, viewId, entityKey, version, requiredSliceTypes)

Steps:
1) SliceRepositoryPort.batchGet(tenantId, sliceKeys)
2) missingPolicy는 기본 FAIL_CLOSED (누락 slice 있으면 Err)
3) 결과 조합 JSON은 결정적으로 생성 (sliceTypes 입력 순서가 결과에 영향 주지 않도록 정렬 규칙 고정)
4) v1 최소 스펙: CORE 중심
   - v1에서는 “의미 있는 view 정의”는 없으므로, QueryView는 “요청한 sliceTypes를 묶어서” 반환하는 최소 API다.

---

2. Determinism Rules (Must)

- 동일 입력 → 동일 문자열 결과
- sliceTypes는:
  - v1: 내부적으로 LEXICOGRAPHIC 정렬 후 조합 (결정성)

2-1. viewId 의미 (v1 고정)
- v1에서 viewId는 “응답 래퍼/네임스페이스” 용도이며, 계약 검증을 하지 않는다.
- (v2+) ViewDefinitionContract를 도입하면 viewId 검증(허용 목록/정의 로딩)을 추가한다.

---

3. Acceptance Criteria

- missing slice 존재 시 Err (fail-closed)
- sliceTypes 순서를 바꿔 요청해도 동일 결과(JSON ordering 고정)

