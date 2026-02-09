RFC-IMPL-006 — Incremental Path (ChangeSet + ImpactMap) v1.1

Status: Accepted
Created: 2026-01-25
Scope: INCREMENTAL slicing 구현을 위한 “필수 계약/알고리즘/포트”를 구현 수준으로 고정
Depends on: RFC-V4-001, RFC-V4-002, RFC-V4-006, RFC-IMPL-002, RFC-IMPL-004
Audience: Runtime Developers / Contract Authors
Non-Goals: compiler optimization(RFC-009), sink(v1.2+)

0. Executive Summary

본 RFC는 v1.1에서 FULL==INCREMENTAL 불변식을 만족시키기 위해 필요한 구현 항목을 고정한다.

---

1. Required Components (Must)

1-1. ChangeSetRepositoryPort (신규)
- put/get + idempotency(hash invariant)

1-2. ImpactMap (RuleSet 계약 구성 요소)
- 변경 경로(pathSelector) → sliceType[] 매핑 SSOT
- “조인으로 생성된 sliceType”도 포함

1-2-1. pathSelector 최소 문법 (v1.1, MUST)
- 표현: RFC6901 JSON Pointer (RFC-V4-001/002 일관)
  - 예: `/title`, `/brand/id`, `/skus/0/price`
- 배열 처리:
  - v1.1에서는 “인덱스 포함 pointer”만 지원 (`/skus/0/price`)
  - 와일드카드(`*`)는 금지 (결정성/범위 모호)
- 정렬/결정성:
  - ImpactMap의 키(JSON Pointer)는 LEXICOGRAPHIC 정렬이 SSOT
  - impactedSliceTypes는 LEXICOGRAPHIC 정렬 후 처리 (입력 순서 무관)
- unknown path 처리: FAIL_CLOSED (매칭이 없으면 오류)

1-2-2. tombstone 최소 스펙 (v1.1, MUST)
- “재생성 결과 0건”인 sliceType은 tombstone으로 기록될 수 있어야 함 (RFC-V4-001)
- tombstone의 표현/필드/저장 위치는 SliceRecord 확장 또는 별도 속성으로 고정 (별도 RFC-IMPL-006-1에서 확정 가능)

1-3. SlicingEngine (도메인 서비스)
- FULL과 INCREMENTAL이 동일 결과를 산출하도록 구현

---

2. Acceptance Criteria (Must Pass)

- FULL 결과 == INCREMENTAL 결과 (동일 입력/동일 계약 기준)
- tombstone 정책이 계약으로 고정되어 동작

