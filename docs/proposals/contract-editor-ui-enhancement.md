# Ultimate Contract DX/UX Platform

**Subtitle**: Thinking Environment for Contract-First Systems

**Version**: 2.0 (Execution Ready)

---

## 0. Executive Thesis (DX 관점 단일 문장)

> Contract Editor는 YAML 편집기가 아니라
> **"계약 변경의 의미·원인·결과를 실시간으로 설명하는 사고 보조 시스템"**이어야 한다.

---

## 1. DX First Principles (비협상 원칙)

이 시스템은 아래 원칙을 **절대 위반하지 않음**.

### 1-1. Meaning > Syntax
- 사용자는 "문법"이 아니라 **의미 단위**로 사고함
- 모든 피드백은 field / slice / view / pipeline 의미 기준으로 제공

### 1-2. Causality is Visible
- "왜 안 됐는지"는 추론 대상이 아님
- 시스템은 **항상 원인 트리를 명시적으로 제공**

### 1-3. Invalid State is Unrepresentable (DX 버전)
- 만들 수 없는 상태는 에러가 아니라 **선택지에서 제거**
- 에러 메시지는 **항상 해결 경로를 포함**

### 1-4. Time-Aware & Change-Aware
- 모든 변경은 before/after, 영향 범위, 재생성 필요 여부가 **즉시 드러남**

### 1-5. System Mental Model Alignment
- 개발자의 머릿속 모델과 시스템 내부 상태가 **동일한 언어로 표현**

### 1-6. Keyboard-First, Mouse-Optional
- 모든 기능은 **키보드만으로 접근 가능**
- 마우스는 보조 수단, 키보드가 주 수단
- Power user를 위한 경로가 기본 경로

### 1-7. Zero-Config Sensible Defaults
- 설정 없이도 80% 생산성 달성
- 커스터마이징은 **옵트인**, 기본은 최적화된 상태

---

## 2. 전체 실행 전략 (Execution Strategy)

### 2-1. 전략 핵심

| 원칙 | 설명 |
|------|------|
| **기존 시스템을 깨지 않는다** | Runtime, Registry 그대로 유지 |
| **DX는 레이어로 얹는다** | 읽기 → 설명 → 시뮬레이션 → 출력 순서 |
| **Git / YAML / Runtime은 SSOT** | UI는 절대 SSOT가 아님 |
| **UI는 "사고 가속 레이어"** | 쓰기 도구가 아님 |

### 2-2. Product Definition

**이 시스템은 무엇인가:**
- Contract-First 시스템을 위한 **SOTA급 DX Control Plane**
- YAML은 SSOT, UI는 사고 가속기

**이 시스템은 무엇이 아닌가:**
- Form 기반 Visual Builder ❌
- 단순 Monaco Wrapper ❌
- Git 대체 ❌
- 느린 웹앱 ❌

---

## 3. 시스템 아키텍처 (Logical Architecture)

```
┌──────────────────────────────────────────────────────────────────┐
│ Contract DX UI (Admin)                                           │
│                                                                  │
│ Monaco Editor + DX Panels                                        │
│  ├─ Meaning Panel          (커서 위치 기반 의미 표시)             │
│  ├─ Validation Panel       (L0~L4 검증 결과)                     │
│  ├─ Change Summary         (Semantic Diff)                       │
│  ├─ Impact / Causality Graph (의존성 + 원인 트리)                │
│  └─ Explain Mode           (계약 설명 자동 생성)                  │
│                                                                  │
└───────────────────────────▲──────────────────────────────────────┘
                            │
                            │ HTTP (contract-first APIs)
                            │
┌───────────────────────────┴──────────────────────────────────────┐
│ Contract Intelligence Service (NEW)                              │
│                                                                  │
│ 1. Contract Loader / Indexer      → ContractDescriptor 생성     │
│ 2. Validation Engine (L0~L4)      → 다단계 검증                  │
│ 3. Dependency Graph Builder       → 정적 의존성 그래프           │
│ 4. Semantic Diff Engine           → 의미 단위 변경 분석          │
│ 5. Explain / Why Engine           → 원인 트리 생성               │
│ 6. Simulation Adapter             → 기존 Runtime 활용            │
│                                                                  │
└───────────────────────────▲──────────────────────────────────────┘
                            │
                            │ read-only / simulate
                            │
┌───────────────────────────┴──────────────────────────────────────┐
│ Existing Runtime (변경 없음)                                      │
│                                                                  │
│ - Contract Registry (DynamoDB)                                   │
│ - Slice Engine                                                   │
│ - Sample Generator                                               │
│ - Ingest / Query APIs                                            │
└──────────────────────────────────────────────────────────────────┘
```

### 3-1. Contract Intelligence Service 핵심 모델

#### ContractDescriptor (공통 입력)

```kotlin
data class ContractDescriptor(
    val id: String,
    val kind: ContractKind,        // ENTITY_SCHEMA | RULESET | VIEW_DEFINITION | SINKRULE
    val version: String,
    val filePath: String?,
    val parsedAst: YamlNode,
    val semanticInfo: SemanticInfo
)

data class SemanticInfo(
    val fields: List<FieldInfo>,
    val slicesProduced: List<String>,
    val slicesRequired: List<String>,
    val refs: List<ContractRef>    // 참조하는 다른 계약들
)
```

#### Dependency Graph 모델

```kotlin
enum class NodeKind { Schema, RuleSet, ViewDef, Sink }
enum class EdgeKind { defines, uses, produces, requires }

data class DependencyGraph(
    val nodes: Map<String, GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNode(
    val id: String,
    val kind: NodeKind,
    val contract: ContractDescriptor
)

data class GraphEdge(
    val from: String,
    val to: String,
    val kind: EdgeKind
)
```

---

## 4. Core UX Structure (Thinking Environment)

### 4-1. 단일 화면 원칙 (One Cognitive Surface)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ⌘K Command Palette (Always Available)                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ Breadcrumb: ENTITY_SCHEMA > entity.product.v1 > fields > price          │
├───────────────────────────────────┬─────────────────────────────────────┤
│ Editor (Code)                     │ Meaning Panel                       │
│                                   │ ┌─────────────────────────────────┐ │
│ ┌───────────────────────────────┐ │ │ Field: price                    │ │
│ │ kind: ENTITY_SCHEMA           │ │ │ Type: long (required)           │ │
│ │ id: entity.product.v1    ←──┼─┼─┤ Status: ✓ Valid                  │ │
│ │ fields:                       │ │ │                                 │ │
│ │   - name: price          ◀───┼─┼─┤ Used by:                        │ │
│ │     type: long                │ │ │  • RuleSet: pricing-core       │ │
│ │     required: true            │ │ │  • Views: product-detail (2)    │ │
│ │                               │ │ │                                 │ │
│ │ [Minimap with Semantic Color] │ │ │ Slice Impact:                   │ │
│ └───────────────────────────────┘ │ │  ⚠ PRICE slice regen required  │ │
│                                   │ └─────────────────────────────────┘ │
├───────────────────────────────────┴─────────────────────────────────────┤
│ Causality / Impact Graph (Why / Because)        [Expand ↕] [Depth: 2]  │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │  [EntitySchema] ──defines──▶ [RuleSet] ──produces──▶ [Slice]       │ │
│ │       ↓                           ↓                      ↓          │ │
│ │  entity.product.v1         ruleset.core.v1           PRICE         │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ Change Summary (Semantic Diff)                              [Collapse] │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ + Field added: reviewCount (int)                                    │ │
│ │ ○ No breaking changes                                               │ │
│ │ ⚠ Re-ingest required: YES                                          │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│ [⌘S Save] [Simulate ▶] [Explain ?] [Export ↓] [Create PR]              │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4-2. Command Palette (⌘K) - Power User Gateway

```
┌─────────────────────────────────────────────────────────────────────┐
│ ⌘K  Search contracts, actions, fields...                            │
├─────────────────────────────────────────────────────────────────────┤
│ Recent                                                               │
│   entity.product.v1                              ENTITY_SCHEMA      │
│   ruleset.core.v1                                RULESET            │
├─────────────────────────────────────────────────────────────────────┤
│ Actions                                                              │
│   ▶ Simulate this contract                       ⌘⇧S               │
│   ? Explain this contract                        ⌘E                 │
│   ↓ Export as patch                              ⌘⇧E               │
│   ⎇ Jump to definition                           ⌘Click / F12      │
│   ⤢ Find all references                          ⇧F12              │
├─────────────────────────────────────────────────────────────────────┤
│ Templates                                                            │
│   + New EntitySchema                             ⌘N E               │
│   + New RuleSet                                  ⌘N R               │
│   + New ViewDefinition                           ⌘N V               │
└─────────────────────────────────────────────────────────────────────┘
```

**핵심 단축키:**

| 단축키 | 기능 | 설명 |
|--------|------|------|
| `⌘K` | Command Palette | 모든 기능 진입점 |
| `⌘S` | Save | 저장 + 자동 검증 |
| `⌘⇧S` | Simulate | 파이프라인 시뮬레이션 |
| `⌘E` | Explain | 현재 계약 설명 |
| `F12` | Go to Definition | 참조된 계약으로 이동 |
| `⇧F12` | Find References | 이 계약을 참조하는 곳 |
| `⌘.` | Quick Fix | 에러 수정 제안 |
| `⌘Z` | Semantic Undo | 의미 단위 되돌리기 |
| `⌘⇧Z` | Semantic Redo | 의미 단위 다시 실행 |

---

## 5. Phase-by-Phase Execution Plan (실제 일정)

### 총 일정: 11.5주 (약 3개월)

```
Week  1 ──────────────────────────────────────────────────────────────▶
      │ Phase 0: Groundwork (1주)
      │ - ContractDescriptor Index
      │ - Dependency Graph 모델

Week  2-3 ────────────────────────────────────────────────────────────▶
      │ Phase 1: Meaning-Aware Editing (2주)
      │ - Monaco + Cursor Hook
      │ - Meaning Panel
      │ - Validation L0~L3

Week  4-5 ────────────────────────────────────────────────────────────▶
      │ Phase 2: Semantic Diff (1.5주)
      │ - Semantic Diff Engine
      │ - Change Summary UI

Week  5-7 ────────────────────────────────────────────────────────────▶
      │ Phase 3: Why Engine (2주, 핵심)
      │ - Failure Reason Model
      │ - Slice/View Debug Adapter
      │ - Why Panel UI

Week  7-8 ────────────────────────────────────────────────────────────▶
      │ Phase 4: Impact Graph (1.5주)
      │ - Graph Render
      │ - Navigation

Week  9-10 ───────────────────────────────────────────────────────────▶
      │ Phase 5: Simulation (2주)
      │ - Sample Generator
      │ - Pipeline Simulation UI

Week  11 ─────────────────────────────────────────────────────────────▶
      │ Phase 6: Git Output (1주)
      │ - Patch Export
      │ - PR Template
```

---

### Phase 0. Groundwork (1주, 병렬 가능)

**목표:** DX 기능을 얹을 기반 데이터 모델 확정

#### 0-1. ContractDescriptor Index

```kotlin
// 모든 Contract를 이 구조로 메모리/스토리지 인덱싱
// 이게 모든 DX 기능의 공통 입력

data class ContractDescriptor(
    val id: String,
    val kind: ContractKind,
    val version: String,
    val filePath: String?,
    val parsedAst: YamlNode,
    val semanticInfo: SemanticInfo
)

data class SemanticInfo(
    val fields: List<FieldInfo>,
    val slicesProduced: List<String>,
    val slicesRequired: List<String>,
    val refs: List<ContractRef>
)
```

#### 0-2. Dependency Graph 모델 고정

```kotlin
enum class NodeKind { Schema, RuleSet, ViewDef, Sink }
enum class EdgeKind { defines, uses, produces, requires }

// Graph는 정적 truth로 취급
// 런타임 상태가 아닌, 계약 정의만으로 구성
```

**완료 기준:**
- [ ] ContractDescriptor로 모든 계약 로드 가능
- [ ] DependencyGraph 생성 및 조회 가능
- [ ] API: `GET /api/contracts/graph`

---

### Phase 1. Meaning-Aware Editing (2주)

**목표:** "지금 이 줄이 시스템에서 무슨 의미인지" 즉시 보이게

**사용자 워크플로우:**
```
1. Contract Editor 진입
2. YAML 수정
3. 커서 이동
4. Meaning Panel 자동 갱신
```

#### 1-1. Monaco + Cursor Hook

```typescript
// 커서 위치 → AST Node 매핑
// AST Node → semanticInfo 조회

interface CursorContext {
  line: number;
  column: number;
  astPath: string[];        // ["fields", "0", "name"]
  semanticNode: SemanticNode | null;
}

function onCursorChange(position: Position): CursorContext {
  const astPath = yamlAstLocator.findPath(position);
  const semanticNode = semanticIndex.lookup(astPath);
  return { ...position, astPath, semanticNode };
}
```

#### 1-2. Meaning Panel 구현

```typescript
interface MeaningPanelData {
  // 정의 대상
  definition: {
    type: 'field' | 'slice' | 'view' | 'rule';
    name: string;
    dataType?: string;
    required?: boolean;
  };

  // 사용처
  usedBy: {
    ruleSets: ContractRef[];
    views: ContractRef[];
  };

  // 파생 효과
  impact: {
    slicesAffected: string[];
    regenRequired: boolean;
  };
}
```

**표시 예시:**
```
Field: price
Type: long (required)
Used by:
  - RuleSet: pricing-core
  - Views: product-detail, search-result
Slice impact:
  - PRICE slice regeneration required
```

#### 1-3. Validation L0~L3 실시간 실행

```typescript
// debounce 300ms
const validateDebounced = debounce(async (yaml: string) => {
  const results = await api.validate(yaml);
  setValidationResults(results);
}, 300);

// 결과를 line marker + panel로 분리 표시
interface ValidationResult {
  level: 'L0' | 'L1' | 'L2' | 'L3' | 'L4';
  line: number;
  message: string;
  fix?: QuickFix;
}
```

| Level | 의미 | UX 표현 | 예시 |
|-------|------|---------|------|
| L0 | Syntax | 빨간 밑줄 | `YAML parse error at line 5` |
| L1 | Shape | 주황 밑줄 | `Field 'price' must be number` |
| L2 | Semantic | 노란 밑줄 | `RuleSet must have at least one slice` |
| L3 | Cross-Ref | 파란 밑줄 | `Referenced RuleSet 'xyz' not found` |
| L4 | Runtime-ish | 회색 경고 | `PRICE slice would fail with sample` |

**완료 기준:**
- [ ] YAML 편집 중 Meaning Panel 항상 동기화
- [ ] L0~L3 통과 여부 명확히 표시
- [ ] Quick Fix 기본 동작 (⌘.)

---

### Phase 2. Semantic Diff & Change Awareness (1.5주)

**목표:** "무엇이 바뀌었는지"를 사람이 해석하지 않게

**사용자 워크플로우:**
```
1. YAML 수정
2. Change Summary 자동 생성
3. Re-ingest / Breaking 여부 즉시 확인
```

#### 2-1. Semantic Diff Engine

```kotlin
// before/after AST 비교
// 의미 단위 변경 추출

data class SemanticChange(
    val type: ChangeType,
    val target: String,           // "fields.reviewCount"
    val before: Any?,
    val after: Any?,
    val breaking: Boolean,
    val affectedSlices: List<String>,
    val affectedViews: List<String>
)

enum class ChangeType {
    FieldAdded,
    FieldRemoved,
    FieldTypeChanged,
    FieldRequiredChanged,
    RuleChanged,
    SliceAdded,
    SliceRemoved,
    RefChanged
}

fun computeSemanticDiff(
    before: ContractDescriptor,
    after: ContractDescriptor
): List<SemanticChange>
```

#### 2-2. Change Summary UI

```
┌─────────────────────────────────────────────────────────────────┐
│ CHANGE SUMMARY                                      [Copy] [PR] │
├─────────────────────────────────────────────────────────────────┤
│ + Field added: reviewCount (int)                                │
│ ~ Field modified: price (long → decimal)           ⚠ BREAKING  │
│ - Field removed: legacyCode                        ⚠ BREAKING  │
├─────────────────────────────────────────────────────────────────┤
│ Breaking changes: 2                                              │
│ Slice impact:                                                    │
│   - PRICE slice schema changed                                  │
│   - REVIEW slice added                                          │
├─────────────────────────────────────────────────────────────────┤
│ Views affected:                                                  │
│   - product-detail (field added, field modified)                │
│   - search-result (no impact)                                   │
├─────────────────────────────────────────────────────────────────┤
│ Re-ingest required: YES                                         │
│ Estimated affected entities: ~50,000                            │
└─────────────────────────────────────────────────────────────────┘
```

**완료 기준:**
- [ ] PR 설명에 그대로 복붙 가능한 요약 생성
- [ ] Breaking change 자동 감지
- [ ] Re-ingest 필요 여부 판단

---

### Phase 3. Causality / Why Engine (2주, 핵심)

**목표:** "왜 안 됐지?" 질문을 시스템이 대신 답함

**사용자 워크플로우:**
```
1. View 결과 이상 감지
2. 해당 Contract 열기
3. Why Panel 자동 설명 확인
```

#### 3-1. Failure Reason Model

```kotlin
data class WhyExplanation(
    val symptom: String,           // "PRICE slice missing"
    val causeChain: List<Cause>,
    val lastEvaluated: Instant?
)

data class Cause(
    val order: Int,
    val description: String,
    val expected: String?,
    val actual: String?,
    val relatedContract: ContractRef?,
    val fixSuggestion: String?
)
```

**표현 예시:**
```
PRICE slice missing
Because:
  1. RuleSet pricing-core did not emit PRICE
  2. impactMap mismatch
     - expected: /price
     - actual: /pricing/price
Last evaluated: 45s ago

[Show full trace] [Jump to RuleSet] [Fix impactMap]
```

#### 3-2. Slice / View Debug Adapter

```kotlin
// 기존 runtime 결과를 "원인 구조"로 재포맷

interface DebugAdapter {
    fun explainSliceFailure(
        entityKey: String,
        sliceType: String
    ): WhyExplanation

    fun explainViewFailure(
        entityKey: String,
        viewName: String
    ): WhyExplanation
}
```

#### 3-3. Why Panel UI

```typescript
interface WhyPanelProps {
  explanation: WhyExplanation;
  onJumpToContract: (ref: ContractRef) => void;
  onApplyFix: (cause: Cause) => void;
}

// 트리 구조로 표시
// 시간 정보 포함 (last evaluated)
// 각 원인에서 관련 계약으로 이동 가능
```

**완료 기준:**
- [ ] 로그를 보지 않고도 원인 파악 가능
- [ ] Cause에서 관련 계약으로 1-click 이동
- [ ] Fix suggestion 제공

---

### Phase 4. Impact Graph & Navigation (1.5주)

**목표:** 변경의 반경을 시각적으로 즉시 파악

**사용자 워크플로우:**
```
1. Contract 선택
2. Impact Graph 자동 포커싱
3. 영향받는 노드 하이라이트
4. 클릭 이동
```

#### 4-1. Graph 구현

```typescript
interface ImpactGraphProps {
  graph: DependencyGraph;
  focusNodeId: string;
  depth: 1 | 2 | 3 | 'all';
  filter: 'all' | 'affected';
  layout: 'tree' | 'force' | 'hierarchical';
}

// 변경 노드 기준 BFS로 영향 노드 계산
function computeAffectedNodes(
  graph: DependencyGraph,
  changedNodeId: string,
  depth: number
): Set<string>
```

#### 4-2. Graph 상호작용

```
[Depth: 1] [Depth: 2] [Depth: 3] [All]
[Filter: Affected only] [Filter: All]
[Layout: Tree] [Layout: Force] [Layout: Hierarchical]
```

- 노드 클릭 → 해당 Contract Editor로 이동
- 엣지 호버 → 관계 설명 표시
- 변경된 노드 → 빨간색 하이라이트
- 영향받는 노드 → 주황색 하이라이트

**완료 기준:**
- [ ] Graph depth 2로 기본 렌더
- [ ] 변경 노드 기준 영향 노드 하이라이트
- [ ] 클릭으로 Contract 이동

---

### Phase 5. Sample Playground & Simulation (2주)

**목표:** "배포 전 미래 보기"

**사용자 워크플로우:**
```
1. Generate Sample
2. Simulate Pipeline
3. Slice / View 미리보기
4. 이전 버전과 diff
```

#### 5-1. Pipeline Simulation UI

```
┌─────────────────────────────────────────────────────────────────┐
│ Pipeline Simulation                                [Run ▶] [⟳] │
├──────────────────┬──────────────────┬───────────────────────────┤
│ 1. RawData       │ 2. ChangeSet     │ 3. Slices                 │
│ ┌──────────────┐ │ ┌──────────────┐ │ ┌───────────────────────┐ │
│ │ {            │ │ │ entityKey:   │ │ │ CORE: ✓              │ │
│ │   "sku": ... │→│ │   PRODUCT#...│→│ │ PRICE: ✓             │ │
│ │   "price":...│ │ │ changes: ... │ │ │ MEDIA: ⚠ (partial)   │ │
│ │ }            │ │ │              │ │ │ INVENTORY: ✓         │ │
│ └──────────────┘ │ └──────────────┘ │ └───────────────────────┘ │
├──────────────────┴──────────────────┴───────────────────────────┤
│ 4. View Output                                    [Diff ◐] [Raw]│
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ PRODUCT_DETAIL view generated successfully                  │ │
│ │ {                                                           │ │
│ │   "sku": "SKU-001",                                        │ │
│ │   "name": "상품명",                                         │ │
│ │ + "reviewCount": 42,        ← NEW FIELD                    │ │
│ │   "price": 29900                                           │ │
│ │ }                                                           │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

#### 5-2. 구현 작업

```kotlin
// Deterministic sample generator
interface SampleGenerator {
    fun generateSample(schema: ContractDescriptor): RawData
}

// Runtime simulate adapter
interface SimulationAdapter {
    suspend fun simulate(
        rawData: RawData,
        contracts: List<ContractDescriptor>
    ): SimulationResult
}

data class SimulationResult(
    val changeSet: ChangeSet,
    val slices: Map<String, Slice>,
    val view: JsonNode?,
    val errors: List<SimulationError>
)
```

**완료 기준:**
- [ ] 샘플 데이터 자동 생성
- [ ] 파이프라인 단계별 결과 표시
- [ ] 실패 시 단계별 원인 표시

---

### Phase 6. Git-Friendly Output (1주)

**목표:** DX는 올리고, Git은 건드리지 않기

**사용자 워크플로우:**
```
1. Change Summary 확인
2. Patch Export
3. 로컬 Git commit
4. (선택) Create PR
```

#### 6-1. Patch Export

```kotlin
// unified diff 생성
fun exportPatch(
    before: ContractDescriptor,
    after: String  // modified YAML
): UnifiedPatch

// PR 템플릿 자동 생성
fun generatePRDescription(
    changes: List<SemanticChange>
): String
```

**DX 원칙:**
- Git Workflow **절대 침범하지 않음**
- UI는 "가속기", Git은 SSOT
- Draft는 기본 local-only

**완료 기준:**
- [ ] Unified diff export
- [ ] PR 템플릿 자동 생성
- [ ] Copy to clipboard

---

## 6. 실제 개발자 하루 워크플로우

### Before (현재)

```
IDE → YAML 수정 → Build → Restart → Logs 확인 → 추론 → 다시 수정
      ↑___________________________________________________|
```

**문제점:**
- 피드백 루프가 느림 (분 단위)
- "왜 안 됐지?" 추론 필요
- 변경 영향 파악 어려움

### After (SOTA DX)

```
Contract Editor
    │
    ├─▶ Edit (YAML)
    │
    ├─▶ Meaning 확인 (실시간)
    │
    ├─▶ Validation 확인 (300ms)
    │
    ├─▶ Change Summary 확인 (자동)
    │
    ├─▶ Explain / Why 확인 (클릭)
    │
    ├─▶ Simulate (3초)
    │
    ├─▶ Patch Export
    │
    └─▶ Git Commit
```

**개선점:**
- 피드백 루프 초 단위
- "왜 안 됐지?" 시스템이 답변
- 변경 영향 즉시 파악

---

## 7. 운영 가드레일

### 7-1. 안전 원칙

| 원칙 | 설명 |
|------|------|
| **Draft는 local-only** | 저장 전까지 서버에 영향 없음 |
| **Runtime 호출은 simulate-only** | 실제 데이터 변경 불가 |
| **Production write 절대 불가** | UI에서 prod 계약 직접 수정 불가 |
| **모든 API read/derive only** | 부수효과 없는 조회/계산만 |

### 7-2. 권한 모델

```kotlin
enum class EditorPermission {
    READ,           // 조회만
    SIMULATE,       // 시뮬레이션
    DRAFT,          // 로컬 수정
    EXPORT,         // 패치 내보내기
    // WRITE 없음 - Git이 SSOT
}
```

---

## 8. 기술 스택 & 성능 예산

### 8-1. Frontend Stack

```
React 19 + TypeScript 5.7
├── Monaco Editor (YAML mode)
├── @monaco-editor/react
├── ReactFlow (Impact Graph)
├── Zustand (State Management)
├── TanStack Query (API Caching)
└── Tailwind CSS (Styling)
```

### 8-2. Backend API

```yaml
endpoints:
  # 계약 CRUD
  GET    /api/contracts/{kind}                    # List by kind
  GET    /api/contracts/{kind}/{id}               # Get contract

  # Validation
  POST   /api/contracts/validate                  # Multi-level validation

  # Analysis
  GET    /api/contracts/graph                     # Full dependency graph
  GET    /api/contracts/{kind}/{id}/dependencies  # Impact graph data
  GET    /api/contracts/{kind}/{id}/references    # Who uses this
  GET    /api/contracts/{kind}/{id}/explain       # Explain mode data

  # Why Engine
  POST   /api/contracts/why                       # Failure explanation

  # Simulation
  POST   /api/contracts/simulate                  # Pipeline simulation

  # Diff
  POST   /api/contracts/diff                      # Semantic diff

  # Export
  POST   /api/contracts/export/patch              # Unified patch
  POST   /api/contracts/export/pr                 # PR description
```

### 8-3. Performance Budget

| Metric | Target | Why |
|--------|--------|-----|
| Initial Load | < 2s | First meaningful paint |
| Keystroke Response | < 16ms | 60fps feeling |
| Validation (L0-L3) | < 100ms | Real-time feedback |
| Simulation | < 3s | Acceptable wait |
| Graph Render | < 500ms | Smooth interaction |
| Command Palette | < 50ms | Instant feel |

**최적화 전략:**
- Validation은 debounce (300ms)
- Graph는 visible viewport만 렌더
- Simulation은 Web Worker에서 실행
- Contract list는 virtual scroll

---

## 9. 성공 기준 (실행 기준)

### 9-1. 정량 지표

| Metric | Before | After | 측정 방법 |
|--------|--------|-------|----------|
| "왜 안 됐지?" 질문 빈도 | 하루 10회+ | < 1회 | 사용자 피드백 |
| Contract 변경 → 영향 파악 | 5-30분 | < 10초 | 태스크 완료 시간 |
| 신규 개발자 온보딩 | 3일 | 1.5일 | 첫 PR까지 시간 |
| PR 리뷰 코멘트 수 | 5-10개 | < 3개 | GitHub 통계 |
| 계약 관련 버그 | 월 3-5건 | < 1건 | 이슈 트래커 |

### 9-2. 정성 체크리스트

**신규 개발자:**
- [ ] "왜 이 View가 안 나오는지" 1분 내 설명 가능

**Senior:**
- [ ] Contract 변경 → 영향 파악 10초 이내

**PR:**
- [ ] Change Summary 그대로 복붙해서 PR 설명 작성

---

## 10. Anti-Patterns to Avoid

### ❌ 하지 말아야 할 것들

1. **Form Builder로 빠지지 말 것**
   - 드래그앤드롭 UI → 복잡도 폭발
   - YAML 직접 편집이 더 빠름

2. **실시간 저장 (Auto-save) 하지 말 것**
   - 의도치 않은 변경 위험
   - 명시적 Save가 안전

3. **Git을 대체하려 하지 말 것**
   - UI는 가속기
   - YAML 파일이 SSOT

4. **AI에 과의존하지 말 것**
   - Rule-based가 먼저
   - AI는 보조 (Phase 3+)

5. **모든 기능을 첫 화면에 넣지 말 것**
   - Progressive disclosure
   - Command Palette로 접근

---

## 11. 최종 결론

> 이 플랜을 그대로 실행하면:
>
> - **Contract는 설정 파일이 아니라 설계 언어**
> - **UI는 화면이 아니라 사고 증폭기**
> - **DX는 "편하다"가 아니라 생각 속도가 빨라짐**

---

## 다음 스텝

**선택:**

1. **Phase 0-1 즉시 시작** - ContractDescriptor + Graph 모델 구현
2. **MVP 범위 축소** - 2주짜리 DX Spike 설계
3. **이 문서를 RFC/ADR로 공식화**
4. **Semantic Diff / Why Engine 알고리즘 상세 설계**
5. **Backend API 상세 스펙 (OpenAPI) 작성**
