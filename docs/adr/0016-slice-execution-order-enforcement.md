# ADR-0016: Slice 실행 순서/의존성 런타임 강제

**Status**: Accepted  
**Date**: 2026-02  
**Deciders**: Architecture Team  
**RFC**: RFC-018 (slice)

---

## Context

SlicingEngine이 `ruleSet.slices` 순서 그대로 순회하며, TopoSort/의존성 검증이 없었습니다. ENRICHED가 CORE보다 먼저 오면 Slice-to-Slice JOIN 실패 가능성이 있었습니다.

## Decision

**Slice 실행 순서 런타임 강제**를 채택합니다.

### 구현 완료

- **의존성 자동 추론** — SliceKind.ENRICHMENT → CORE 의존, joins.targetSliceType
- **계약 검증 시점** — GatedContractRegistryAdapter.loadRuleSetContract 시 DAG 검증
- **병렬 실행** — Wave별 병렬 (toWaves), 동일 Wave 내 Slice는 async/awaitAll
- **설명 가능성** — SliceExecutionStep.reason ("ENRICHMENT 슬라이스는 CORE 이후 실행")
- **에러 메시지** — "Slice dependency cycle detected. Involved slices: X → Y"

### SliceExecutionPlanner

- RuleSet의 slices를 의존성 순서로 정렬
- cycles 감지 시 Err 반환 (fail-closed)

## Consequences

### Positive

- ✅ Slice-to-Slice JOIN 도입 시 안전성 보장
- ✅ DAG 검증으로 런타임 오류 사전 방지

### Negative

- ⚠️ 계약에 dependsOn 명시 필요 시 DX 부담

---

## 참고

- [RFC-018 (slice)](../rfc_archive/2026-02/RFC-018-slice-execution-order-enforcement.md)
