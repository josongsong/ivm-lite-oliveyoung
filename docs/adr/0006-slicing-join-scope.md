# ADR-0006: Slicing Join 허용 범위 및 중간 IR 정의

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-006

---

## Context

slicing 단계에서 join 허용 범위, 중간 IR의 정의 방식, 계약(Contract)로 고정해야 할 불변식을 명확히 규정해야 했습니다.

문제점:
- slicing에서 join을 전면 금지하면 Slice 수가 과도하게 증가
- 반대로 join을 무제한 허용하면 결정성·증분·영향 분석이 붕괴
- "중간 IR을 따로 둬야 하나?"에 대한 설계 기준이 불명확

## Decision

**Slicing Join 허용 범위 및 중간 IR 정의**를 명확히 규정합니다.

### 핵심 결론

1. **slicing에서 가벼운 join은 허용**
2. **join은 타입·상한·선택 규칙이 계약으로 고정되어야 함**
3. **"중간 IR"은 새 레이어가 아니라 SliceType의 한 종류로 정의**
4. **RefIndexSlice / EnrichmentSlice 패턴을 표준으로 채택**
5. **View에서는 최종 조합 1회만 허용**

### SliceType 분류

**기본 SliceType** (RFC-001):
- CoreSlice: join 금지 (SliceType: CORE)
- VariantSlice: join 금지 (SliceType: VARIANT)
- DiscoverySlice: join 금지 (SliceType: DISCOVERY)
- PolicySlice: join 금지 (SliceType: POLICY)
- ContentIndexSlice: join 금지 (SliceType: CONTENT_INDEX)

**중간 IR SliceType** (RFC-006):
- RefIndexSlice: lookup 전용 slice (중간 IR 역할, SliceType: REF_INDEX)
- EnrichmentSlice: 제한적 join 허용 slice (SliceType: ENRICHMENT)

**컴파일러 SliceType** (RFC-009):
- Target: 최종 서빙용 slice (SliceType: TARGET)

### slicing 단계에서 join 허용 원칙

**허용되는 join**:
- 1:1 또는 1:small-N (상한이 계약으로 고정됨)
- 사전 계산된 snapshot/RefIndexSlice 기반 lookup
- 결정적 선택 규칙(pick rule)이 명시된 경우
- MANY_TO_ONE / ONE_TO_ONE만 허용
- fanout cardinality 상한: 1:N 허용, N:M 금지
- join depth = 1 (단일 홉 LOOKUP만 허용)

**금지되는 join**:
- 다대다 join (카디널리티 폭발) - N:M 금지
- 외부 상태(user/session/실시간) 결합
- 탐색형/조건형 런타임 쿼리
- 상한·선택 규칙이 없는 join
- chain join 금지 (순환/연쇄 조인 금지)
- 순환 참조 및 다단계 조인 금지

### 중간 IR의 공식 정의 방식

**레이어 분리 금지**:
- `ir/` 같은 새로운 레이어를 만들지 않음
- 중간 표현은 SliceType으로만 존재함

**RefIndexSlice (표준 중간 IR)**:
- 목적: 다른 Slice가 lookup하기 위한 정규화된 참조
- 예시: BrandSummarySlice, CategoryPathSlice, PricePolicySlice
- 특징: 다수 consumer slice에서 재사용, 변경 영향 범위가 명확, 증분/재빌드 계산이 쉬움

**EnrichmentSlice (결과 확장)**:
- EnrichmentSlice는 다음 중 하나 이상의 조합:
  - CoreSlice + RefIndexSlice (권장, v1+)
  - CoreSlice + RawData (직접 join, 제한적, v0)
  - CoreSlice + RefIndexSlice + RawData (혼합, 제한적)
- 표시용 소량 필드 embed만 허용

### JoinSpec 계약 (필수)

JoinSpec 필수 필드:
- sourceSliceType
- maxRows (카디널리티 상한)
- pickRule (결정적 선택 규칙)
- required (missing 허용 여부)
- versioningRule (참조 버전 고정 규칙)
- joinSources, entityType, keyResolver
- joinProjection (포함 필드 명시)
- joinImpact (참조 엔티티 변경 시 영향 SliceType)

**계약 검증 규칙**:
- JoinSpec 계약 검증 시점에 제한 사항 강제
- 위반 시 계약 로드 실패 (fail-closed)
- JoinSpec 허용 범위는 계약 스키마에 명시적으로 포함되어야 함

### View 단계의 join 규칙

- View에서의 join은 최종 조합 1회만 허용
- View는 slicing 결과를 조합할 뿐, 의미 계산을 하지 않음

### 증분(ChangeSet)과의 연계

**RefIndexSlice의 장점**:
- Brand 변경 → BrandSummarySlice만 재빌드
- 이를 참조하는 EnrichmentSlice만 영향받음
- 전체 ProductSlice 재빌드 방지 가능

**JoinSpec 기반 영향 계산**:
- 어떤 SliceType이 어떤 RefIndexSlice에 의존하는지 명시됨
- ChangeSet → Impacted SliceType 계산 가능
- JoinSpec의 joinImpact 필드가 명시적으로 영향받는 SliceType을 선언
- 명시되지 않은 Join 영향은 FAIL_CLOSED

## Consequences

### Positive

- ✅ slicing 단계에서 제한적 join을 허용하여 Slice 수 최적화
- ✅ join은 JoinSpec 계약으로만 정의되어 결정성 보장
- ✅ 중간 IR은 SliceType의 한 종류로 정의되어 구조 단순화
- ✅ RefIndexSlice / EnrichmentSlice 패턴으로 재사용성 향상
- ✅ View는 최종 조합 1회만 수행하여 성능 최적화

### Negative

- ⚠️ JoinSpec 계약 작성 필요
- ⚠️ join 제한으로 인한 설계 복잡도
- ⚠️ RefIndexSlice 관리 오버헤드

### Neutral

- Join 계산 비용
- 중간 IR 저장 공간

---

## 참고

- [RFC-V4-006](../rfc/rfc006.md) - 원본 RFC 문서
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
- [RFC-V4-009](../rfc/rfc009.md) - Rule-Driven Compiler Architecture
