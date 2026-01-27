# ADR-0009: Rule-Driven Compiler Architecture

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-009

---

## Context

컴파일러가 "룰드리븐(Rule-Driven)" 방식으로 중간 aggregated IR을 자동 삽입하고 재사용하도록 설계해야 했습니다.

문제점:
- 여러 target(slice)이 동일한 join/derive를 반복 수행하면 중복 계산 발생
- 저장 공간 낭비
- 일관성 문제 (동일 입력이 다른 결과)

## Decision

**Rule-Driven Compiler Architecture**를 채택합니다.

### 핵심 결론

- 룰드리븐은 내부적으로 3단계로 구현: **RuleRegistry → RuleMatch → PlanRewrite**
- AggregationRule을 SSOT로 저장 (버전 포함)
- 컴파일 시점에 targets/inputs을 넣고 RuleMatch로 어떤 룰이 발동되는지 결정
- 발동된 룰로 PlanRewrite (중간 aggregated IR 삽입/재사용/대체)를 수행
- Depth/Join Law로 검증한 뒤 실행
- **자동 최적화가 아니라, "정해진 규칙에 따른 기계적 재작성"임**

### 룰드리븐 3단계

1. **RuleRegistry**: AggregationRule을 SSOT로 저장 (버전 포함)
2. **RuleMatch**: 컴파일 시점에 targets/inputs을 넣고 어떤 룰이 발동되는지 결정
3. **PlanRewrite**: 발동된 룰로 그래프를 재작성 (aggregated 삽입/재사용/대체)

### Capability 기반 매칭

- **provides**: Slice/Target가 제공하는 capability (예: `join.brand`, `derive.price_bucket`)
- **requires**: Slice/Target가 필요로 하는 capability (예: `join.brand`, `derive.search_tokens`)
- **매칭 원리**: AggregationRule의 `provides`가 target의 `requires`를 커버하면 룰 발동

**Capability 형식 규칙**:
- Capability는 문자열로 표현: `{type}.{name}` (예: `join.brand`, `derive.price_bucket`)
- 타입: `join`, `derive`, `norm`, `serve`
- 이름: 소문자와 언더스코어만 허용 (`[a-z_]+`)

**Capability와 JoinSpec의 관계**:
- Capability는 JoinSpec의 추상화된 표현
- `join.brand` capability는 `JoinSpec(joinSources=[brand-summary@1], target={entityType: BRAND, ...}, ...)`로 구현됨
- provides/requires는 Capability 기반으로 표현하되, 실제 구현은 JoinSpec으로 매핑됨

### 중간 Aggregated IR

**목적**: 여러 target이 공통으로 사용하는 join/derive 결과를 한 번만 계산

**타입**: EnrichmentSlice (RFC-006 참조)

**구성**: EnrichmentSlice는 다음 중 하나 이상의 조합:
- CoreSlice + RefIndexSlice (권장, v1+)
- CoreSlice + RawData (직접 join, 제한적, v0)
- CoreSlice + RefIndexSlice + RawData (혼합, 제한적)

**예시**: `CatalogEnrichedSlice`가 `join.brand`, `join.category_path`를 provides

### AggregationRule 스키마

```kotlin
data class AggregationRule(
    val ruleId: String,
    val version: SemVer,
    val priority: Int,  // 높을수록 우선
    
    val when: RuleCondition,
    val then: RuleAction,
    val constraints: RuleConstraints
)

data class RuleCondition(
    val targetsAll: List<TargetRef>?,  // 모든 target이 매칭되어야 함
    val targetsAny: List<TargetRef>?,  // 하나라도 매칭되면 됨
    val tenantSelector: TenantSelector?
)

data class RuleAction(
    val materializeSlice: SliceRef,  // 중간 IR slice
    val provides: List<Capability>,  // 이 aggregated가 제공하는 capability
    val replaceDirectEdges: Boolean  // 기존 direct join edge를 강제로 제거할지
)
```

### RuleMatch (어떤 룰이 발동되는지)

**매칭 알고리즘 (기계적)**:
1. targetsAll/Any 조건으로 후보 룰 필터링
2. constraints(tier 등) 위반 후보 제거
3. priority DESC로 정렬
4. 충돌(동일 provides 중복) 시 "가장 좁게 커버하는 룰" 또는 "우선순위"로 결정

**불변식**: Contract resolution must be deterministic and single-valued

### PlanRewrite (중간 aggregated IR을 실제로 삽입)

**기본 그래프 (재작성 전)**:
```
L1 ProductCore
  → SearchDoc (join.brand + join.category_path)
  → RecoFeed  (join.brand + join.category_path)
```

**룰 적용 후 그래프 (재작성 후)**:
```
L1 ProductCore
  → CatalogEnriched (aggregated, provides join.brand, join.category_path)
      → SearchDoc (projection only)
      → RecoFeed  (projection only)
```

**재작성 규칙**:
1. materializeSlice 노드 생성 (없으면)
2. materializeSlice.inputs를 registry에서 가져와 그래프에 연결
3. 각 target의 requiredCapabilities 중 룰이 provides 하는 것들을 제거
4. target이 더 이상 조인이 필요 없도록 입력을 materializeSlice로 교체
5. replaceDirectEdges=true면 기존 direct join edge를 강제로 제거
6. 동일 materialize slice가 이미 존재하면 재사용 (중복 생성 금지)

### 검증 (Law Enforcer)

재작성 후에 반드시 검증함 (실패-클로즈):
- cycle 없음
- tier 제한 준수 (L2 aggregated가 L3로 튀면 fail)
- join 제한 준수 (L1에서 join 발생하면 fail)
- produces outputs가 targets를 만족하는지 검증 (coverage)

### SliceDescriptor에 provides/requires를 박아야 함

```kotlin
data class SliceDescriptor(
    val id: String,
    val version: SemVer,
    val sliceType: SliceType,
    val tier: Tier,
    val inputs: List<InputRef>,
    val provides: List<Capability>,  // 이 slice를 만들면 어떤 capability를 제공하는가
    val requires: List<Capability>,  // 이 slice를 만들려면 어떤 capability가 필요한가
    val joinSpecs: List<JoinSpec>?,  // 구체적인 join 구현
    ...
)
```

**예시**:
- `CatalogEnriched` provides: `join.brand`, `join.category_path`
- `SearchDoc` requires: `join.brand`, `join.category_path`, `derive.search_tokens`
- rewrite 후 `SearchDoc`의 requires는 `derive.search_tokens`만 남음

## Consequences

### Positive

- ✅ 룰드리븐 컴파일러는 "룰 매칭 후 그래프 재작성"으로 구현됨
- ✅ AggregationRule이 제공하는 capability를 기준으로 자동 최적화
- ✅ 개발자는 target만 선택하면 자동으로 중간 aggregated slice 삽입
- ✅ 모든 재작성은 결정적이고 멱등적임
- ✅ Law 검증으로 안전성 보장

### Negative

- ⚠️ AggregationRule 작성 및 관리 필요
- ⚠️ Capability 매칭 복잡도
- ⚠️ PlanRewrite 알고리즘 이해 필요

### Neutral

- 컴파일 시간 증가 가능
- RuleRegistry 관리 오버헤드

---

## 참고

- [RFC-V4-009](../rfc/rfc009.md) - 원본 RFC 문서
- [RFC-V4-006](../rfc/rfc006.md) - Slicing Join 허용 범위
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
