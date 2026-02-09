# RFC-IMPL-015: Workflow Canvas - 데이터 파이프라인 시각화

| 항목 | 내용 |
|------|------|
| **상태** | ✅ **Implemented** |
| **작성일** | 2026-01-29 |
| **작성자** | Admin UI Team |
| **관련 RFC** | RFC-IMPL-014 (Admin UI Enhancement) |
| **예상 공수** | BE: 3일, FE: 5일 |
| **우선순위** | P1 - High Impact Feature |

---

## 📋 Executive Summary

데이터 파이프라인을 **인터랙티브 노드 그래프**로 시각화하는 Workflow Canvas 페이지.
RawData → Slice → View → Sink 흐름을 한눈에 파악하고, 클릭으로 상세 탐색 가능.

**핵심 가치:**
- 🎯 **시스템 이해도 향상**: 복잡한 파이프라인을 직관적으로 파악
- 🔍 **빠른 디버깅**: 에러 노드를 시각적으로 즉시 발견
- 📊 **실시간 모니터링**: 처리량, 지연시간 등 핵심 지표 표시
- 🔗 **연결성**: Contract, Pipeline 페이지와 원클릭 연동

---

## 1. 개요

### 1.1 배경

현재 Admin UI에서는 Contracts, Pipeline, Outbox 등을 개별 페이지에서 조회할 수 있지만, 
**전체 데이터 흐름을 한눈에 파악하기 어렵습니다**. 

데이터가 RawData에서 시작하여 Slice → View → Sink로 흐르는 과정을 
**인터랙티브한 캔버스**에서 시각화하면 시스템 이해도와 디버깅 효율이 크게 향상됩니다.

### 1.2 목표

1. **전체 파이프라인 시각화**: RawData → Slice → View → Sink 흐름을 노드-엣지 그래프로 표현
2. **인터랙티브 탐색**: 노드 클릭 시 상세 정보 패널, 드래그/줌/팬 지원
3. **실시간 상태 반영**: 각 단계의 처리 현황, 에러 상태 표시
4. **규칙 연결 시각화**: 어떤 RuleSet/ViewDefinition이 적용되는지 명확히 표시

## 2. 상세 설계

### 2.1 페이지 구조

```
┌─────────────────────────────────────────────────────────────────────┐
│  Workflow Canvas                                           [전체보기] │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ 📋 Entity 선택: [PRODUCT ▼] [BRAND ▼] [CATEGORY ▼] [전체]     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                                                               │   │
│  │                    ╔══════════════╗                           │   │
│  │                    ║   RawData    ║                           │   │
│  │                    ║   PRODUCT    ║                           │   │
│  │                    ╚══════╦═══════╝                           │   │
│  │                           │                                   │   │
│  │                    ┌──────┴──────┐                            │   │
│  │                    │ RuleSet v1  │ ← 규칙 노드                │   │
│  │                    └──────┬──────┘                            │   │
│  │           ┌───────────────┼───────────────┐                   │   │
│  │           │               │               │                   │   │
│  │     ╔═════╧═════╗   ╔═════╧═════╗   ╔═════╧═════╗            │   │
│  │     ║   CORE    ║   ║   PRICE   ║   ║   MEDIA   ║ ← Slices   │   │
│  │     ╚═════╤═════╝   ╚═════╤═════╝   ╚═════╤═════╝            │   │
│  │           │               │               │                   │   │
│  │           └───────────────┼───────────────┘                   │   │
│  │                    ┌──────┴──────┐                            │   │
│  │                    │ ViewDef v1  │ ← 규칙 노드                │   │
│  │                    └──────┬──────┘                            │   │
│  │           ┌───────────────┼───────────────┐                   │   │
│  │     ╔═════╧═════╗   ╔═════╧═════╗   ╔═════╧═════╗            │   │
│  │     ║  DETAIL   ║   ║  SEARCH   ║   ║   LIST    ║ ← Views    │   │
│  │     ╚═════╤═════╝   ╚═════╤═════╝   ╚═════╤═════╝            │   │
│  │           │               │               │                   │   │
│  │           └───────────────┼───────────────┘                   │   │
│  │                    ┌──────┴──────┐                            │   │
│  │                    │  SinkRule   │ ← 규칙 노드                │   │
│  │                    └──────┬──────┘                            │   │
│  │                    ╔══════╧═══════╗                           │   │
│  │                    ║  OpenSearch  ║ ← Sink                    │   │
│  │                    ╚══════════════╝                           │   │
│  │                                                               │   │
│  │                        [Canvas - Drag/Zoom/Pan]               │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────── Detail Panel (노드 클릭 시) ───────────┐              │
│  │ ┌───────────────────────────────────────────────┐ │              │
│  │ │ 📦 PRODUCT - CORE Slice                       │ │              │
│  │ │                                               │ │              │
│  │ │ 필드: sku, name, price, category              │ │              │
│  │ │ 처리량: 1,234 records/min                      │ │              │
│  │ │ 마지막 업데이트: 2분 전                         │ │              │
│  │ │                                               │ │              │
│  │ │ [Contract 보기] [Pipeline 보기]                │ │              │
│  │ └───────────────────────────────────────────────┘ │              │
│  └───────────────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 노드 타입

| 노드 타입 | 색상 | 아이콘 | 설명 |
|-----------|------|--------|------|
| **RawData** | Cyan | `Database` | 원본 데이터 (엔티티별) |
| **RuleSet** | Purple (작은 노드) | `GitBranch` | 슬라이싱 규칙 |
| **Slice** | Green | `Layers` | 슬라이스 (CORE, PRICE 등) |
| **ViewDefinition** | Orange (작은 노드) | `Eye` | 뷰 조합 규칙 |
| **View** | Magenta | `Monitor` | 조합된 뷰 (DETAIL, SEARCH 등) |
| **SinkRule** | Yellow (작은 노드) | `ArrowRight` | 싱크 규칙 |
| **Sink** | Red | `Cloud` | 외부 시스템 (OpenSearch, Kafka) |

### 2.3 노드 상태

```typescript
type NodeStatus = 
  | 'healthy'    // 녹색 테두리, 정상 동작
  | 'warning'    // 황색 테두리, 지연 또는 경고
  | 'error'      // 적색 테두리, 오류 발생
  | 'inactive'   // 회색 테두리, 비활성
```

### 2.4 데이터 모델

```typescript
// 워크플로우 노드
interface WorkflowNode {
  id: string
  type: 'rawdata' | 'ruleset' | 'slice' | 'viewdef' | 'view' | 'sinkrule' | 'sink'
  label: string
  entityType?: string      // PRODUCT, BRAND 등
  contractRef?: string     // 연관 Contract ID
  status: NodeStatus
  stats?: {
    recordCount: number
    throughput: number     // records/min
    lastUpdated: string
    errorCount?: number
  }
  position: { x: number; y: number }
}

// 워크플로우 엣지 (연결선)
interface WorkflowEdge {
  id: string
  source: string           // 소스 노드 ID
  target: string           // 타겟 노드 ID
  label?: string           // 엣지 라벨 (optional)
  animated?: boolean       // 애니메이션 여부
}

// 전체 워크플로우
interface WorkflowGraph {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}
```

### 2.5 API 엔드포인트

```yaml
# 워크플로우 그래프 조회
GET /api/workflow/graph
Query:
  entityType?: string     # 특정 엔티티만 필터
Response:
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]

# 노드 상세 정보
GET /api/workflow/nodes/{nodeId}
Response:
  node: WorkflowNode
  relatedContracts: Contract[]
  recentActivity: ActivityItem[]

# 워크플로우 통계
GET /api/workflow/stats
Response:
  entityTypes: string[]
  totalNodes: number
  totalEdges: number
  healthySummary: { healthy: number, warning: number, error: number }
```

### 2.6 캔버스 구현

#### 2.6.1 기술 스택

| 옵션 | 라이브러리 | 장점 | 단점 |
|------|-----------|------|------|
| **Option A** | `@xyflow/react` (React Flow) | 노드 기반 UI 특화, 풍부한 기능 | 번들 크기 (100KB+) |
| **Option B** | `d3.js` + SVG | 완전한 커스텀 가능 | 구현 복잡도 높음 |
| **Option C** | Canvas API 직접 구현 | 최고 성능 | 개발 시간 많이 필요 |

**권장: Option A (`@xyflow/react`)**
- 드래그/줌/팬 기본 지원
- 커스텀 노드 스타일링 가능
- 미니맵, 컨트롤 패널 기본 제공
- 엣지 애니메이션 지원

#### 2.6.2 캔버스 인터랙션

```typescript
// 줌/팬 컨트롤
interface CanvasControls {
  zoomIn: () => void
  zoomOut: () => void
  fitView: () => void      // 전체 보기
  resetView: () => void    // 초기 위치
}

// 노드 선택 이벤트
interface NodeSelectEvent {
  node: WorkflowNode
  position: { x: number; y: number }
}

// 레이아웃 옵션
type LayoutDirection = 'TB' | 'LR'  // Top-Bottom, Left-Right
```

### 2.7 컴포넌트 구조

```
admin-ui/src/features/workflow/
├── index.ts
├── api/
│   └── workflowApi.ts          # API 호출
├── model/
│   └── types.ts                # 타입 정의
├── ui/
│   ├── Workflow.tsx            # 메인 페이지
│   ├── Workflow.css
│   ├── WorkflowCanvas.tsx      # 캔버스 컴포넌트
│   ├── WorkflowCanvas.css
│   ├── WorkflowSidebar.tsx     # 엔티티/규칙 리스트
│   └── WorkflowDetailPanel.tsx # 노드 상세 패널
├── components/
│   ├── nodes/
│   │   ├── RawDataNode.tsx     # RawData 노드
│   │   ├── SliceNode.tsx       # Slice 노드
│   │   ├── ViewNode.tsx        # View 노드
│   │   ├── SinkNode.tsx        # Sink 노드
│   │   └── RuleNode.tsx        # 규칙 노드 (작은 크기)
│   ├── edges/
│   │   └── AnimatedEdge.tsx    # 애니메이션 엣지
│   └── controls/
│       ├── CanvasControls.tsx  # 줌/팬 컨트롤
│       └── Minimap.tsx         # 미니맵
└── lib/
    └── layoutEngine.ts         # 자동 레이아웃 계산
```

### 2.8 노드 디자인

```css
/* RawData 노드 - 가장 큰 노드 */
.node-rawdata {
  width: 160px;
  height: 80px;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.2), rgba(0, 212, 255, 0.05));
  border: 2px solid var(--accent-cyan);
  border-radius: 12px;
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
}

/* Slice 노드 */
.node-slice {
  width: 120px;
  height: 60px;
  background: linear-gradient(135deg, rgba(0, 255, 136, 0.2), rgba(0, 255, 136, 0.05));
  border: 2px solid var(--accent-green);
  border-radius: 8px;
}

/* Rule 노드 - 작은 사각형 */
.node-rule {
  width: 100px;
  height: 40px;
  background: rgba(136, 85, 255, 0.15);
  border: 1px dashed var(--accent-purple);
  border-radius: 4px;
  font-size: 0.75rem;
}

/* Sink 노드 */
.node-sink {
  width: 140px;
  height: 70px;
  background: linear-gradient(135deg, rgba(255, 0, 170, 0.2), rgba(255, 0, 170, 0.05));
  border: 2px solid var(--accent-magenta);
  border-radius: 12px;
}

/* 상태별 테두리 애니메이션 */
.node-status-healthy { animation: pulse-green 2s infinite; }
.node-status-warning { animation: pulse-yellow 1s infinite; }
.node-status-error { animation: pulse-red 0.5s infinite; }
```

### 2.9 엣지 애니메이션

```css
/* 데이터 흐름 애니메이션 */
.edge-animated {
  stroke-dasharray: 5;
  animation: flow 0.5s linear infinite;
}

@keyframes flow {
  from { stroke-dashoffset: 10; }
  to { stroke-dashoffset: 0; }
}

/* 에러 엣지 */
.edge-error {
  stroke: var(--status-error);
  stroke-width: 2;
}
```

## 3. 사용자 시나리오

### 3.1 전체 파이프라인 파악

1. 사용자가 "Workflow" 메뉴 클릭
2. 전체 엔티티의 파이프라인이 캔버스에 표시
3. 마우스 휠로 줌, 드래그로 팬
4. 각 노드의 상태(색상)로 현재 상황 파악

### 3.2 특정 엔티티 추적

1. 상단 필터에서 "PRODUCT" 선택
2. PRODUCT 관련 노드만 하이라이트
3. RawData → CORE/PRICE/MEDIA Slice → DETAIL/SEARCH View → OpenSearch 흐름 확인

### 3.3 문제 디버깅

1. 빨간색(error) 노드 발견
2. 노드 클릭 → 상세 패널에서 에러 정보 확인
3. "Contract 보기" 클릭 → Contract 상세 페이지 이동
4. 문제 해결 후 돌아오기

### 3.4 규칙 변경 영향 파악

1. RuleSet 노드 클릭
2. 상세 패널에서 영향받는 Slice 목록 확인
3. 해당 Slice들이 연결된 View, Sink까지 추적
4. 변경 시 영향 범위 파악

## 4. 백엔드 구현

### 4.1 WorkflowController

```kotlin
@RestController
@RequestMapping("/api/workflow")
class WorkflowController(
    private val workflowService: WorkflowService
) {
    @GetMapping("/graph")
    suspend fun getGraph(
        @RequestParam entityType: String?
    ): WorkflowGraphDto {
        return workflowService.buildGraph(entityType)
    }
    
    @GetMapping("/nodes/{nodeId}")
    suspend fun getNodeDetail(
        @PathVariable nodeId: String
    ): WorkflowNodeDetailDto {
        return workflowService.getNodeDetail(nodeId)
    }
    
    @GetMapping("/stats")
    suspend fun getStats(): WorkflowStatsDto {
        return workflowService.getStats()
    }
}
```

### 4.2 WorkflowService

```kotlin
@Service
class WorkflowService(
    private val contractRegistry: ContractRegistryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val metricsCollector: MetricsCollectorPort
) {
    suspend fun buildGraph(entityType: String?): WorkflowGraphDto {
        val contracts = contractRegistry.listAllContracts()
        val nodes = mutableListOf<WorkflowNodeDto>()
        val edges = mutableListOf<WorkflowEdgeDto>()
        
        // 1. EntitySchema → RawData 노드
        contracts.filterIsInstance<EntitySchemaContract>()
            .filter { entityType == null || it.entityType == entityType }
            .forEach { schema ->
                nodes.add(createRawDataNode(schema))
            }
        
        // 2. RuleSet → Rule 노드 + Slice 노드
        contracts.filterIsInstance<RuleSetContract>()
            .forEach { ruleSet ->
                nodes.add(createRuleNode(ruleSet))
                ruleSet.slices.forEach { slice ->
                    nodes.add(createSliceNode(ruleSet.entityType, slice))
                    edges.add(createEdge(ruleSet.id, slice.type.name))
                }
            }
        
        // 3. ViewDefinition → View 노드
        contracts.filterIsInstance<ViewDefinitionContract>()
            .forEach { viewDef ->
                nodes.add(createViewNode(viewDef))
                viewDef.requiredSlices.forEach { slice ->
                    edges.add(createEdge(slice.name, viewDef.meta.id))
                }
            }
        
        // 4. SinkRule → Sink 노드
        contracts.filterIsInstance<SinkRuleContract>()
            .forEach { sinkRule ->
                nodes.add(createSinkNode(sinkRule))
            }
        
        return WorkflowGraphDto(
            nodes = layoutNodes(nodes),  // 자동 레이아웃
            edges = edges
        )
    }
}
```

## 5. 구현 계획 (BE/FE 분리)

---

## 🔧 BACKEND 구현 (3일)

### BE Phase 1: 도메인 모델 & API (Day 1)

#### 1.1 파일 구조

```
src/main/kotlin/com/oliveyoung/ivmlite/
├── apps/admin/
│   ├── routes/
│   │   └── WorkflowRoutes.kt          # 라우팅
│   └── handlers/
│       └── WorkflowHandler.kt          # 핸들러
└── pkg/workflow/                        # 워크플로우 도메인
    ├── domain/
    │   ├── WorkflowGraph.kt            # 그래프 도메인 모델
    │   ├── WorkflowNode.kt             # 노드 모델
    │   └── WorkflowEdge.kt             # 엣지 모델
    ├── application/
    │   └── WorkflowService.kt          # 비즈니스 로직
    ├── adapters/
    │   └── WorkflowGraphBuilder.kt     # 그래프 빌더
    └── ports/
        └── WorkflowPort.kt             # 포트 인터페이스
```

#### 1.2 도메인 모델

```kotlin
// WorkflowNode.kt
package com.oliveyoung.ivmlite.pkg.workflow.domain

enum class NodeType {
    RAWDATA,      // 원본 데이터
    RULESET,      // 슬라이싱 규칙
    SLICE,        // 슬라이스
    VIEW_DEF,     // 뷰 정의
    VIEW,         // 뷰
    SINK_RULE,    // 싱크 규칙
    SINK          // 외부 시스템
}

enum class NodeStatus {
    HEALTHY,      // 정상
    WARNING,      // 경고
    ERROR,        // 오류
    INACTIVE      // 비활성
}

data class NodeStats(
    val recordCount: Long,
    val throughput: Double,       // records/min
    val latencyP99Ms: Long?,
    val errorCount: Long,
    val lastUpdatedAt: Instant?
)

data class NodePosition(
    val x: Double,
    val y: Double
)

data class WorkflowNode(
    val id: String,
    val type: NodeType,
    val label: String,
    val entityType: String?,
    val contractId: String?,
    val status: NodeStatus,
    val stats: NodeStats?,
    val position: NodePosition,
    val metadata: Map<String, Any> = emptyMap()
)
```

```kotlin
// WorkflowEdge.kt
data class WorkflowEdge(
    val id: String,
    val source: String,
    val target: String,
    val sourceHandle: String? = null,
    val targetHandle: String? = null,
    val label: String? = null,
    val animated: Boolean = false,
    val style: EdgeStyle = EdgeStyle.DEFAULT
)

enum class EdgeStyle {
    DEFAULT,
    DASHED,
    ANIMATED,
    ERROR
}
```

```kotlin
// WorkflowGraph.kt
data class WorkflowGraph(
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>,
    val metadata: GraphMetadata
)

data class GraphMetadata(
    val entityTypes: List<String>,
    val totalNodes: Int,
    val totalEdges: Int,
    val healthSummary: HealthSummary,
    val lastUpdatedAt: Instant
)

data class HealthSummary(
    val healthy: Int,
    val warning: Int,
    val error: Int,
    val inactive: Int
)
```

#### 1.3 API DTO

```kotlin
// WorkflowDtos.kt
data class WorkflowGraphResponse(
    val nodes: List<NodeDto>,
    val edges: List<EdgeDto>,
    val metadata: MetadataDto
)

data class NodeDto(
    val id: String,
    val type: String,
    val data: NodeDataDto,
    val position: PositionDto
)

data class NodeDataDto(
    val label: String,
    val entityType: String?,
    val contractId: String?,
    val status: String,
    val stats: StatsDto?,
    val metadata: Map<String, Any>
)

data class EdgeDto(
    val id: String,
    val source: String,
    val target: String,
    val sourceHandle: String?,
    val targetHandle: String?,
    val label: String?,
    val animated: Boolean,
    val type: String?   // 'smoothstep', 'bezier', etc.
)

data class NodeDetailResponse(
    val node: NodeDto,
    val relatedContracts: List<ContractSummary>,
    val upstreamNodes: List<String>,
    val downstreamNodes: List<String>,
    val recentActivity: List<ActivityItem>,
    val metrics: NodeMetrics
)
```

---

### BE Phase 2: 서비스 & 그래프 빌더 (Day 2)

#### 2.1 WorkflowService

```kotlin
// WorkflowService.kt
@Service
class WorkflowService(
    private val contractRegistry: ContractRegistryPort,
    private val graphBuilder: WorkflowGraphBuilder,
    private val metricsCollector: MetricsCollectorPort,
    private val outboxRepo: OutboxRepositoryPort
) {
    
    suspend fun getGraph(entityType: String? = null): WorkflowGraph {
        // 1. 모든 Contract 로드
        val contracts = contractRegistry.listAllContracts()
        
        // 2. 그래프 빌드
        val graph = graphBuilder.build(contracts, entityType)
        
        // 3. 실시간 상태 & 통계 주입
        val enrichedNodes = graph.nodes.map { node ->
            enrichNodeWithStats(node)
        }
        
        return graph.copy(
            nodes = enrichedNodes,
            metadata = buildMetadata(enrichedNodes, graph.edges)
        )
    }
    
    suspend fun getNodeDetail(nodeId: String): NodeDetail {
        val graph = getGraph()
        val node = graph.nodes.find { it.id == nodeId }
            ?: throw DomainError.NotFound("Node not found: $nodeId")
        
        return NodeDetail(
            node = node,
            relatedContracts = findRelatedContracts(node),
            upstreamNodes = findUpstream(graph, nodeId),
            downstreamNodes = findDownstream(graph, nodeId),
            recentActivity = getRecentActivity(node),
            metrics = getNodeMetrics(node)
        )
    }
    
    private suspend fun enrichNodeWithStats(node: WorkflowNode): WorkflowNode {
        val stats = when (node.type) {
            NodeType.RAWDATA -> getRawDataStats(node.entityType)
            NodeType.SLICE -> getSliceStats(node.entityType, node.label)
            NodeType.VIEW -> getViewStats(node.label)
            NodeType.SINK -> getSinkStats(node.label)
            else -> null
        }
        
        val status = calculateStatus(stats)
        
        return node.copy(stats = stats, status = status)
    }
    
    private fun calculateStatus(stats: NodeStats?): NodeStatus {
        if (stats == null) return NodeStatus.INACTIVE
        return when {
            stats.errorCount > 0 -> NodeStatus.ERROR
            stats.latencyP99Ms != null && stats.latencyP99Ms > 5000 -> NodeStatus.WARNING
            stats.throughput < 1.0 && stats.recordCount > 0 -> NodeStatus.WARNING
            else -> NodeStatus.HEALTHY
        }
    }
}
```

#### 2.2 GraphBuilder - 자동 레이아웃

```kotlin
// WorkflowGraphBuilder.kt
@Component
class WorkflowGraphBuilder {
    
    companion object {
        // 레이아웃 상수
        const val LAYER_GAP_Y = 150.0
        const val NODE_GAP_X = 180.0
        const val CANVAS_PADDING = 50.0
    }
    
    fun build(contracts: List<Contract>, entityTypeFilter: String?): WorkflowGraph {
        val nodes = mutableListOf<WorkflowNode>()
        val edges = mutableListOf<WorkflowEdge>()
        
        // Entity별로 그룹핑
        val entitySchemas = contracts.filterIsInstance<EntitySchemaContract>()
            .filter { entityTypeFilter == null || it.entityType == entityTypeFilter }
        
        val ruleSets = contracts.filterIsInstance<RuleSetContract>()
        val viewDefs = contracts.filterIsInstance<ViewDefinitionContract>()
        val sinkRules = contracts.filterIsInstance<SinkRuleContract>()
        
        var entityIndex = 0
        
        entitySchemas.forEach { schema ->
            val entityType = schema.entityType
            val baseX = entityIndex * 400.0 + CANVAS_PADDING
            
            // Layer 0: RawData
            val rawDataNode = createRawDataNode(schema, baseX, 0)
            nodes.add(rawDataNode)
            
            // Layer 1: RuleSet (작은 노드)
            val ruleSet = ruleSets.find { it.entityType == entityType }
            if (ruleSet != null) {
                val ruleNode = createRuleSetNode(ruleSet, baseX, 1)
                nodes.add(ruleNode)
                edges.add(createEdge(rawDataNode.id, ruleNode.id))
                
                // Layer 2: Slices
                ruleSet.slices.forEachIndexed { sliceIdx, slice ->
                    val sliceX = baseX + (sliceIdx - ruleSet.slices.size / 2.0) * NODE_GAP_X
                    val sliceNode = createSliceNode(entityType, slice, sliceX, 2)
                    nodes.add(sliceNode)
                    edges.add(createEdge(ruleNode.id, sliceNode.id, animated = true))
                }
            }
            
            // Layer 3: ViewDef + Views
            val relatedViews = viewDefs.filter { 
                it.requiredSlices.any { s -> 
                    ruleSet?.slices?.any { rs -> rs.type == s } == true 
                }
            }
            
            relatedViews.forEachIndexed { viewIdx, viewDef ->
                val viewX = baseX + (viewIdx - relatedViews.size / 2.0) * NODE_GAP_X
                val viewNode = createViewNode(viewDef, viewX, 4)
                nodes.add(viewNode)
                
                // Slice → View 엣지
                viewDef.requiredSlices.forEach { sliceType ->
                    val sliceNodeId = "${entityType}_${sliceType.name}"
                    edges.add(createEdge(sliceNodeId, viewNode.id))
                }
            }
            
            // Layer 4: SinkRule + Sink
            val relatedSinks = sinkRules.filter { sink ->
                sink.input.entityTypes?.contains(entityType) == true
            }
            
            relatedSinks.forEachIndexed { sinkIdx, sinkRule ->
                val sinkX = baseX + (sinkIdx - relatedSinks.size / 2.0) * NODE_GAP_X
                val sinkNode = createSinkNode(sinkRule, sinkX, 6)
                nodes.add(sinkNode)
            }
            
            entityIndex++
        }
        
        return WorkflowGraph(nodes, edges, buildMetadata(nodes, edges))
    }
    
    private fun createRawDataNode(schema: EntitySchemaContract, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "rawdata_${schema.entityType}",
            type = NodeType.RAWDATA,
            label = schema.entityType,
            entityType = schema.entityType,
            contractId = schema.meta.id,
            status = NodeStatus.HEALTHY,
            stats = null,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf("fieldCount" to schema.fields.size)
        )
    }
    
    private fun createSliceNode(entityType: String, slice: SliceDefinition, x: Double, layer: Int): WorkflowNode {
        return WorkflowNode(
            id = "${entityType}_${slice.type.name}",
            type = NodeType.SLICE,
            label = slice.type.name,
            entityType = entityType,
            contractId = null,
            status = NodeStatus.HEALTHY,
            stats = null,
            position = NodePosition(x, layer * LAYER_GAP_Y + CANVAS_PADDING),
            metadata = mapOf(
                "buildType" to (slice.buildRules::class.simpleName ?: "unknown")
            )
        )
    }
    
    // ... 다른 노드 생성 메서드들
}
```

---

### BE Phase 3: Routes & Handler (Day 3)

#### 3.1 라우팅

```kotlin
// WorkflowRoutes.kt
fun Route.workflowRoutes(handler: WorkflowHandler) {
    route("/api/workflow") {
        // 전체 그래프 조회
        get("/graph") {
            val entityType = call.request.queryParameters["entityType"]
            val response = handler.getGraph(entityType)
            call.respond(response)
        }
        
        // 노드 상세 정보
        get("/nodes/{nodeId}") {
            val nodeId = call.parameters["nodeId"] 
                ?: throw BadRequestException("nodeId required")
            val response = handler.getNodeDetail(nodeId)
            call.respond(response)
        }
        
        // 워크플로우 통계
        get("/stats") {
            val response = handler.getStats()
            call.respond(response)
        }
        
        // 특정 엔티티의 전체 흐름 추적
        get("/trace/{entityKey}") {
            val entityKey = call.parameters["entityKey"]
                ?: throw BadRequestException("entityKey required")
            val response = handler.traceEntity(entityKey)
            call.respond(response)
        }
    }
}
```

#### 3.2 핸들러

```kotlin
// WorkflowHandler.kt
class WorkflowHandler(
    private val workflowService: WorkflowService
) {
    suspend fun getGraph(entityType: String?): WorkflowGraphResponse {
        val graph = workflowService.getGraph(entityType)
        return graph.toResponse()
    }
    
    suspend fun getNodeDetail(nodeId: String): NodeDetailResponse {
        val detail = workflowService.getNodeDetail(nodeId)
        return detail.toResponse()
    }
    
    suspend fun getStats(): WorkflowStatsResponse {
        val stats = workflowService.getStats()
        return stats.toResponse()
    }
    
    suspend fun traceEntity(entityKey: String): EntityTraceResponse {
        val trace = workflowService.traceEntity(entityKey)
        return trace.toResponse()
    }
}
```

#### 3.3 AdminModule 등록

```kotlin
// AdminModule.kt에 추가
val workflowModule = module {
    single { WorkflowGraphBuilder() }
    single { 
        WorkflowService(
            contractRegistry = get(),
            graphBuilder = get(),
            metricsCollector = get(),
            outboxRepo = get()
        )
    }
    single { WorkflowHandler(get()) }
}
```

---

## 🎨 FRONTEND 구현 (5일)

### FE Phase 1: 설정 & 기본 구조 (Day 1)

#### 1.1 의존성 설치

```bash
cd admin-ui
npm install @xyflow/react dagre
npm install -D @types/dagre
```

#### 1.2 파일 구조

```
admin-ui/src/features/workflow/
├── index.ts                          # Feature export
├── api/
│   └── workflowApi.ts                # API 호출
├── model/
│   ├── types.ts                      # TypeScript 타입
│   ├── useWorkflowStore.ts           # Zustand 스토어
│   └── constants.ts                  # 상수 정의
├── ui/
│   ├── Workflow.tsx                  # 메인 페이지
│   ├── Workflow.css
│   ├── WorkflowCanvas.tsx            # React Flow 캔버스
│   ├── WorkflowCanvas.css
│   ├── WorkflowToolbar.tsx           # 상단 툴바 (필터, 줌)
│   ├── WorkflowSidebar.tsx           # 좌측 엔티티 리스트
│   ├── WorkflowDetailPanel.tsx       # 우측 상세 패널
│   └── WorkflowDetailPanel.css
├── components/
│   ├── nodes/
│   │   ├── index.ts                  # 노드 타입 매핑
│   │   ├── BaseNode.tsx              # 공통 노드 래퍼
│   │   ├── BaseNode.css
│   │   ├── RawDataNode.tsx
│   │   ├── SliceNode.tsx
│   │   ├── ViewNode.tsx
│   │   ├── SinkNode.tsx
│   │   └── RuleNode.tsx              # 작은 규칙 노드
│   ├── edges/
│   │   ├── AnimatedEdge.tsx
│   │   └── ErrorEdge.tsx
│   └── controls/
│       ├── ZoomControls.tsx
│       └── MiniMap.tsx
└── lib/
    ├── layoutEngine.ts               # Dagre 레이아웃
    └── graphUtils.ts                 # 그래프 유틸
```

#### 1.3 타입 정의

```typescript
// model/types.ts
import type { Node, Edge } from '@xyflow/react'

// 노드 타입
export type WorkflowNodeType = 
  | 'rawdata' 
  | 'ruleset' 
  | 'slice' 
  | 'viewdef' 
  | 'view' 
  | 'sinkrule' 
  | 'sink'

// 노드 상태
export type NodeStatus = 'healthy' | 'warning' | 'error' | 'inactive'

// 노드 통계
export interface NodeStats {
  recordCount: number
  throughput: number
  latencyP99Ms?: number
  errorCount: number
  lastUpdatedAt?: string
}

// 노드 데이터
export interface WorkflowNodeData {
  label: string
  entityType?: string
  contractId?: string
  status: NodeStatus
  stats?: NodeStats
  metadata: Record<string, unknown>
}

// React Flow 노드 타입
export type WorkflowNode = Node<WorkflowNodeData, WorkflowNodeType>

// React Flow 엣지 타입
export interface WorkflowEdge extends Edge {
  animated?: boolean
  type?: 'smoothstep' | 'bezier' | 'straight'
}

// API 응답
export interface WorkflowGraphResponse {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  metadata: GraphMetadata
}

export interface GraphMetadata {
  entityTypes: string[]
  totalNodes: number
  totalEdges: number
  healthSummary: {
    healthy: number
    warning: number
    error: number
    inactive: number
  }
  lastUpdatedAt: string
}

// 노드 상세 정보
export interface NodeDetailResponse {
  node: WorkflowNode
  relatedContracts: ContractSummary[]
  upstreamNodes: string[]
  downstreamNodes: string[]
  recentActivity: ActivityItem[]
  metrics: NodeMetrics
}

export interface ContractSummary {
  id: string
  kind: string
  version: string
}

export interface ActivityItem {
  timestamp: string
  action: string
  details: string
}

export interface NodeMetrics {
  avgLatencyMs: number
  p99LatencyMs: number
  errorRate: number
  throughputTrend: number[]  // 최근 24시간 시간대별
}
```

#### 1.4 상수 정의

```typescript
// model/constants.ts
export const NODE_COLORS: Record<string, { bg: string; border: string; glow: string }> = {
  rawdata: {
    bg: 'rgba(0, 212, 255, 0.15)',
    border: '#00d4ff',
    glow: 'rgba(0, 212, 255, 0.4)'
  },
  slice: {
    bg: 'rgba(0, 255, 136, 0.15)',
    border: '#00ff88',
    glow: 'rgba(0, 255, 136, 0.4)'
  },
  view: {
    bg: 'rgba(255, 0, 170, 0.15)',
    border: '#ff00aa',
    glow: 'rgba(255, 0, 170, 0.4)'
  },
  sink: {
    bg: 'rgba(255, 136, 0, 0.15)',
    border: '#ff8800',
    glow: 'rgba(255, 136, 0, 0.4)'
  },
  ruleset: {
    bg: 'rgba(136, 85, 255, 0.1)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.3)'
  },
  viewdef: {
    bg: 'rgba(136, 85, 255, 0.1)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.3)'
  },
  sinkrule: {
    bg: 'rgba(136, 85, 255, 0.1)',
    border: '#8855ff',
    glow: 'rgba(136, 85, 255, 0.3)'
  }
}

export const STATUS_COLORS: Record<string, string> = {
  healthy: '#00ff88',
  warning: '#ffcc00',
  error: '#ff4444',
  inactive: '#666666'
}

export const NODE_DIMENSIONS: Record<string, { width: number; height: number }> = {
  rawdata: { width: 160, height: 80 },
  slice: { width: 120, height: 60 },
  view: { width: 140, height: 70 },
  sink: { width: 140, height: 70 },
  ruleset: { width: 100, height: 40 },
  viewdef: { width: 100, height: 40 },
  sinkrule: { width: 100, height: 40 }
}
```

---

### FE Phase 2: 커스텀 노드 컴포넌트 (Day 2)

#### 2.1 BaseNode

```tsx
// components/nodes/BaseNode.tsx
import { memo } from 'react'
import { Handle, Position, type NodeProps } from '@xyflow/react'
import { NODE_COLORS, STATUS_COLORS, NODE_DIMENSIONS } from '../../model/constants'
import type { WorkflowNodeData, WorkflowNodeType } from '../../model/types'
import './BaseNode.css'

interface BaseNodeProps extends NodeProps {
  type: WorkflowNodeType
  icon: React.ReactNode
  children?: React.ReactNode
}

export const BaseNode = memo(function BaseNode({ 
  data, 
  type, 
  icon, 
  selected,
  children 
}: BaseNodeProps) {
  const colors = NODE_COLORS[type]
  const statusColor = STATUS_COLORS[data.status]
  const dimensions = NODE_DIMENSIONS[type]
  
  const isRuleNode = ['ruleset', 'viewdef', 'sinkrule'].includes(type)
  
  return (
    <div
      className={`workflow-node ${type} ${selected ? 'selected' : ''}`}
      style={{
        width: dimensions.width,
        height: dimensions.height,
        background: colors.bg,
        borderColor: selected ? statusColor : colors.border,
        boxShadow: selected 
          ? `0 0 20px ${colors.glow}, 0 0 40px ${colors.glow}`
          : `0 0 10px ${colors.glow}`
      }}
    >
      {/* 상태 인디케이터 */}
      <div 
        className="node-status-indicator"
        style={{ backgroundColor: statusColor }}
      />
      
      {/* 입력 핸들 */}
      <Handle 
        type="target" 
        position={Position.Top}
        className="node-handle"
      />
      
      {/* 컨텐츠 */}
      <div className="node-content">
        <div className="node-icon">{icon}</div>
        <div className="node-label">{data.label}</div>
        {!isRuleNode && data.stats && (
          <div className="node-stats">
            <span className="stat-value">
              {formatNumber(data.stats.throughput)}/min
            </span>
          </div>
        )}
        {children}
      </div>
      
      {/* 출력 핸들 */}
      <Handle 
        type="source" 
        position={Position.Bottom}
        className="node-handle"
      />
    </div>
  )
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toFixed(0)
}
```

```css
/* components/nodes/BaseNode.css */
.workflow-node {
  position: relative;
  border-width: 2px;
  border-style: solid;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  cursor: pointer;
}

.workflow-node:hover {
  transform: translateY(-2px);
}

.workflow-node.selected {
  z-index: 10;
}

/* 규칙 노드 (작은 크기) */
.workflow-node.ruleset,
.workflow-node.viewdef,
.workflow-node.sinkrule {
  border-style: dashed;
  border-radius: 6px;
  padding: 8px;
}

.node-status-indicator {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(1.2); }
}

.node-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.node-icon {
  color: inherit;
  opacity: 0.8;
}

.node-label {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  font-weight: 600;
  text-align: center;
  color: var(--text-primary);
}

.node-stats {
  font-size: 0.7rem;
  color: var(--text-secondary);
}

.node-handle {
  width: 8px;
  height: 8px;
  background: var(--accent-cyan);
  border: 2px solid var(--bg-primary);
}

/* 노드 타입별 아이콘 색상 */
.workflow-node.rawdata .node-icon { color: var(--accent-cyan); }
.workflow-node.slice .node-icon { color: var(--accent-green); }
.workflow-node.view .node-icon { color: var(--accent-magenta); }
.workflow-node.sink .node-icon { color: var(--accent-orange); }
.workflow-node.ruleset .node-icon,
.workflow-node.viewdef .node-icon,
.workflow-node.sinkrule .node-icon { color: var(--accent-purple); }
```

#### 2.2 개별 노드 타입

```tsx
// components/nodes/RawDataNode.tsx
import { memo } from 'react'
import { Database } from 'lucide-react'
import { BaseNode } from './BaseNode'
import type { NodeProps } from '@xyflow/react'
import type { WorkflowNodeData } from '../../model/types'

export const RawDataNode = memo(function RawDataNode(props: NodeProps<WorkflowNodeData>) {
  return (
    <BaseNode {...props} type="rawdata" icon={<Database size={24} />}>
      {props.data.entityType && (
        <span className="entity-badge">{props.data.entityType}</span>
      )}
    </BaseNode>
  )
})

// components/nodes/SliceNode.tsx
import { Layers } from 'lucide-react'
export const SliceNode = memo(function SliceNode(props: NodeProps<WorkflowNodeData>) {
  return <BaseNode {...props} type="slice" icon={<Layers size={20} />} />
})

// components/nodes/ViewNode.tsx
import { Monitor } from 'lucide-react'
export const ViewNode = memo(function ViewNode(props: NodeProps<WorkflowNodeData>) {
  return <BaseNode {...props} type="view" icon={<Monitor size={20} />} />
})

// components/nodes/SinkNode.tsx
import { Cloud } from 'lucide-react'
export const SinkNode = memo(function SinkNode(props: NodeProps<WorkflowNodeData>) {
  return <BaseNode {...props} type="sink" icon={<Cloud size={20} />} />
})

// components/nodes/RuleNode.tsx
import { GitBranch, Eye, ArrowRight } from 'lucide-react'
export const RuleNode = memo(function RuleNode(props: NodeProps<WorkflowNodeData>) {
  const iconMap = {
    ruleset: <GitBranch size={14} />,
    viewdef: <Eye size={14} />,
    sinkrule: <ArrowRight size={14} />
  }
  const nodeType = props.type as 'ruleset' | 'viewdef' | 'sinkrule'
  return <BaseNode {...props} type={nodeType} icon={iconMap[nodeType]} />
})

// components/nodes/index.ts - 노드 타입 매핑
import { RawDataNode } from './RawDataNode'
import { SliceNode } from './SliceNode'
import { ViewNode } from './ViewNode'
import { SinkNode } from './SinkNode'
import { RuleNode } from './RuleNode'

export const nodeTypes = {
  rawdata: RawDataNode,
  slice: SliceNode,
  view: ViewNode,
  sink: SinkNode,
  ruleset: RuleNode,
  viewdef: RuleNode,
  sinkrule: RuleNode,
}
```

---

### FE Phase 3: 캔버스 & 메인 페이지 (Day 3)

#### 3.1 WorkflowCanvas

```tsx
// ui/WorkflowCanvas.tsx
import { useCallback, useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type OnNodesChange,
  type OnEdgesChange,
  type OnConnect,
  BackgroundVariant,
  Panel
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { nodeTypes } from '../components/nodes'
import { NODE_COLORS, STATUS_COLORS } from '../model/constants'
import type { WorkflowNode, WorkflowEdge } from '../model/types'
import './WorkflowCanvas.css'

interface WorkflowCanvasProps {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  onNodeClick: (node: WorkflowNode) => void
  selectedNodeId?: string
}

export function WorkflowCanvas({ 
  nodes: initialNodes, 
  edges: initialEdges,
  onNodeClick,
  selectedNodeId
}: WorkflowCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges)
  
  // 노드 클릭 핸들러
  const handleNodeClick = useCallback((_: React.MouseEvent, node: WorkflowNode) => {
    onNodeClick(node)
  }, [onNodeClick])
  
  // 선택된 노드 하이라이트
  const styledNodes = useMemo(() => {
    return nodes.map(node => ({
      ...node,
      selected: node.id === selectedNodeId
    }))
  }, [nodes, selectedNodeId])
  
  // 커스텀 엣지 스타일
  const styledEdges = useMemo(() => {
    return edges.map(edge => ({
      ...edge,
      style: {
        stroke: edge.animated ? 'var(--accent-cyan)' : 'var(--border-color)',
        strokeWidth: 2
      },
      animated: edge.animated
    }))
  }, [edges])
  
  // 미니맵 노드 색상
  const minimapNodeColor = useCallback((node: WorkflowNode) => {
    const colors = NODE_COLORS[node.type || 'rawdata']
    return colors?.border || '#666'
  }, [])
  
  return (
    <div className="workflow-canvas">
      <ReactFlow
        nodes={styledNodes}
        edges={styledEdges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.1}
        maxZoom={2}
        defaultViewport={{ x: 0, y: 0, zoom: 0.8 }}
        proOptions={{ hideAttribution: true }}
      >
        {/* 배경 그리드 */}
        <Background 
          variant={BackgroundVariant.Dots} 
          gap={20} 
          size={1}
          color="rgba(255, 255, 255, 0.05)"
        />
        
        {/* 줌 컨트롤 */}
        <Controls 
          className="workflow-controls"
          showInteractive={false}
        />
        
        {/* 미니맵 */}
        <MiniMap 
          className="workflow-minimap"
          nodeColor={minimapNodeColor}
          maskColor="rgba(0, 0, 0, 0.8)"
          pannable
          zoomable
        />
        
        {/* 범례 */}
        <Panel position="bottom-left" className="workflow-legend">
          <div className="legend-title">Node Types</div>
          <div className="legend-items">
            {Object.entries(NODE_COLORS).slice(0, 4).map(([type, colors]) => (
              <div key={type} className="legend-item">
                <span 
                  className="legend-dot" 
                  style={{ backgroundColor: colors.border }}
                />
                <span className="legend-label">{type}</span>
              </div>
            ))}
          </div>
        </Panel>
      </ReactFlow>
    </div>
  )
}
```

```css
/* ui/WorkflowCanvas.css */
.workflow-canvas {
  width: 100%;
  height: 100%;
  background: var(--bg-primary);
}

.workflow-canvas .react-flow__edge-path {
  stroke-width: 2;
}

.workflow-canvas .react-flow__edge.animated .react-flow__edge-path {
  stroke-dasharray: 5;
  animation: flow 0.5s linear infinite;
}

@keyframes flow {
  from { stroke-dashoffset: 10; }
  to { stroke-dashoffset: 0; }
}

/* 컨트롤 스타일링 */
.workflow-controls {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.workflow-controls button {
  background: var(--bg-primary);
  border: none;
  color: var(--text-primary);
}

.workflow-controls button:hover {
  background: var(--bg-tertiary);
}

/* 미니맵 스타일링 */
.workflow-minimap {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

/* 범례 */
.workflow-legend {
  background: rgba(0, 0, 0, 0.8);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
}

.legend-title {
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 8px;
  letter-spacing: 0.05em;
}

.legend-items {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-label {
  font-size: 0.75rem;
  color: var(--text-secondary);
  text-transform: capitalize;
}
```

#### 3.2 메인 페이지

```tsx
// ui/Workflow.tsx
import { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { 
  GitBranch, 
  Filter, 
  RefreshCw,
  Maximize2,
  AlertCircle,
  CheckCircle,
  AlertTriangle
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import { Loading, PageHeader } from '@/shared/ui'
import { WorkflowCanvas } from './WorkflowCanvas'
import { WorkflowDetailPanel } from './WorkflowDetailPanel'
import type { WorkflowGraphResponse, WorkflowNode } from '../model/types'
import './Workflow.css'

export function Workflow() {
  const [selectedNode, setSelectedNode] = useState<WorkflowNode | null>(null)
  const [entityFilter, setEntityFilter] = useState<string | null>(null)
  
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['workflow-graph', entityFilter],
    queryFn: () => {
      const params = entityFilter ? `?entityType=${entityFilter}` : ''
      return fetchApi<WorkflowGraphResponse>(`/workflow/graph${params}`)
    },
    refetchInterval: 30000  // 30초마다 자동 갱신
  })
  
  const handleNodeClick = useCallback((node: WorkflowNode) => {
    setSelectedNode(node)
  }, [])
  
  const handleClosePanel = useCallback(() => {
    setSelectedNode(null)
  }, [])
  
  if (isLoading) return <Loading />
  
  const { nodes = [], edges = [], metadata } = data ?? {}
  
  return (
    <div className="workflow-page">
      {/* 헤더 */}
      <div className="workflow-header">
        <PageHeader 
          title="Workflow Canvas" 
          subtitle="데이터 파이프라인을 시각적으로 탐색합니다"
        />
        
        {/* 상태 요약 */}
        {metadata && (
          <div className="health-summary">
            <div className="health-item healthy">
              <CheckCircle size={14} />
              <span>{metadata.healthSummary.healthy}</span>
            </div>
            <div className="health-item warning">
              <AlertTriangle size={14} />
              <span>{metadata.healthSummary.warning}</span>
            </div>
            <div className="health-item error">
              <AlertCircle size={14} />
              <span>{metadata.healthSummary.error}</span>
            </div>
          </div>
        )}
      </div>
      
      {/* 툴바 */}
      <motion.div 
        className="workflow-toolbar"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        {/* 엔티티 필터 */}
        <div className="filter-group">
          <Filter size={14} />
          <select 
            value={entityFilter ?? ''} 
            onChange={(e) => setEntityFilter(e.target.value || null)}
            className="entity-select"
          >
            <option value="">전체 엔티티</option>
            {metadata?.entityTypes.map(type => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
        </div>
        
        {/* 액션 버튼 */}
        <div className="action-group">
          <button 
            className="toolbar-btn"
            onClick={() => refetch()}
            disabled={isFetching}
          >
            <RefreshCw size={14} className={isFetching ? 'spinning' : ''} />
            새로고침
          </button>
          <button className="toolbar-btn">
            <Maximize2 size={14} />
            전체 보기
          </button>
        </div>
        
        {/* 통계 */}
        <div className="stats-group">
          <span className="stat">
            <GitBranch size={12} />
            {metadata?.totalNodes ?? 0} nodes
          </span>
          <span className="stat">
            {metadata?.totalEdges ?? 0} edges
          </span>
        </div>
      </motion.div>
      
      {/* 캔버스 영역 */}
      <div className="workflow-content">
        <motion.div 
          className="canvas-container"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.1 }}
        >
          <WorkflowCanvas 
            nodes={nodes}
            edges={edges}
            onNodeClick={handleNodeClick}
            selectedNodeId={selectedNode?.id}
          />
        </motion.div>
        
        {/* 상세 패널 */}
        {selectedNode && (
          <WorkflowDetailPanel 
            node={selectedNode}
            onClose={handleClosePanel}
          />
        )}
      </div>
    </div>
  )
}
```

```css
/* ui/Workflow.css */
.workflow-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);  /* 헤더 높이 제외 */
  padding: 0;
  overflow: hidden;
}

.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem 2rem 0;
}

.health-summary {
  display: flex;
  gap: 1rem;
}

.health-item {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 600;
}

.health-item.healthy {
  background: rgba(0, 255, 136, 0.1);
  color: var(--accent-green);
}

.health-item.warning {
  background: rgba(255, 204, 0, 0.1);
  color: var(--status-warning);
}

.health-item.error {
  background: rgba(255, 68, 68, 0.1);
  color: var(--status-error);
}

/* 툴바 */
.workflow-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.filter-group,
.action-group,
.stats-group {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.entity-select {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0.5rem 1rem;
  color: var(--text-primary);
  font-size: 0.875rem;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.toolbar-btn:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.toolbar-btn .spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.stats-group .stat {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

/* 캔버스 컨테이너 */
.workflow-content {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

.canvas-container {
  flex: 1;
  position: relative;
}
```

---

### FE Phase 4: 상세 패널 (Day 4)

```tsx
// ui/WorkflowDetailPanel.tsx
import { useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { Link } from 'react-router-dom'
import { 
  X, 
  ExternalLink, 
  Activity, 
  Clock,
  AlertCircle,
  TrendingUp,
  ArrowUpRight,
  ArrowDownRight
} from 'lucide-react'
import { fetchApi } from '@/shared/api'
import type { WorkflowNode, NodeDetailResponse } from '../model/types'
import { STATUS_COLORS } from '../model/constants'
import './WorkflowDetailPanel.css'

interface WorkflowDetailPanelProps {
  node: WorkflowNode
  onClose: () => void
}

export function WorkflowDetailPanel({ node, onClose }: WorkflowDetailPanelProps) {
  const { data: detail, isLoading } = useQuery({
    queryKey: ['workflow-node', node.id],
    queryFn: () => fetchApi<NodeDetailResponse>(`/workflow/nodes/${node.id}`)
  })
  
  const statusColor = STATUS_COLORS[node.data.status]
  
  return (
    <AnimatePresence>
      <motion.div 
        className="detail-panel"
        initial={{ x: 400, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        exit={{ x: 400, opacity: 0 }}
        transition={{ type: 'spring', damping: 25 }}
      >
        {/* 헤더 */}
        <div className="panel-header">
          <div className="panel-title-row">
            <div 
              className="panel-status-dot"
              style={{ backgroundColor: statusColor }}
            />
            <h3 className="panel-title">{node.data.label}</h3>
            <span className="panel-type">{node.type}</span>
          </div>
          <button className="panel-close" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        
        {/* 메인 콘텐츠 */}
        <div className="panel-content">
          {/* 기본 정보 */}
          <section className="panel-section">
            <h4 className="section-title">기본 정보</h4>
            <div className="info-grid">
              {node.data.entityType && (
                <div className="info-item">
                  <span className="info-label">Entity Type</span>
                  <span className="info-value">{node.data.entityType}</span>
                </div>
              )}
              <div className="info-item">
                <span className="info-label">상태</span>
                <span className="info-value" style={{ color: statusColor }}>
                  {node.data.status.toUpperCase()}
                </span>
              </div>
            </div>
          </section>
          
          {/* 통계 */}
          {node.data.stats && (
            <section className="panel-section">
              <h4 className="section-title">
                <Activity size={14} />
                실시간 통계
              </h4>
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-value">
                    {formatNumber(node.data.stats.recordCount)}
                  </div>
                  <div className="stat-label">Total Records</div>
                </div>
                <div className="stat-card">
                  <div className="stat-value">
                    {node.data.stats.throughput.toFixed(1)}
                    <span className="stat-unit">/min</span>
                  </div>
                  <div className="stat-label">Throughput</div>
                </div>
                {node.data.stats.latencyP99Ms && (
                  <div className="stat-card">
                    <div className="stat-value">
                      {node.data.stats.latencyP99Ms}
                      <span className="stat-unit">ms</span>
                    </div>
                    <div className="stat-label">P99 Latency</div>
                  </div>
                )}
                <div className={`stat-card ${node.data.stats.errorCount > 0 ? 'error' : ''}`}>
                  <div className="stat-value">
                    {node.data.stats.errorCount}
                  </div>
                  <div className="stat-label">Errors</div>
                </div>
              </div>
            </section>
          )}
          
          {/* 연결 */}
          {detail && (
            <section className="panel-section">
              <h4 className="section-title">연결된 노드</h4>
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowDownRight size={12} />
                  Upstream ({detail.upstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.upstreamNodes.map(nodeId => (
                    <span key={nodeId} className="connection-tag">
                      {nodeId}
                    </span>
                  ))}
                </div>
              </div>
              <div className="connection-group">
                <div className="connection-label">
                  <ArrowUpRight size={12} />
                  Downstream ({detail.downstreamNodes.length})
                </div>
                <div className="connection-nodes">
                  {detail.downstreamNodes.map(nodeId => (
                    <span key={nodeId} className="connection-tag">
                      {nodeId}
                    </span>
                  ))}
                </div>
              </div>
            </section>
          )}
          
          {/* 관련 Contract */}
          {detail?.relatedContracts && detail.relatedContracts.length > 0 && (
            <section className="panel-section">
              <h4 className="section-title">관련 Contract</h4>
              <div className="contract-list">
                {detail.relatedContracts.map(contract => (
                  <Link 
                    key={contract.id}
                    to={`/contracts/${contract.kind}/${encodeURIComponent(contract.id)}`}
                    className="contract-link"
                  >
                    <span className="contract-kind">{contract.kind}</span>
                    <span className="contract-id">{contract.id}</span>
                    <ExternalLink size={12} />
                  </Link>
                ))}
              </div>
            </section>
          )}
          
          {/* 최근 활동 */}
          {detail?.recentActivity && detail.recentActivity.length > 0 && (
            <section className="panel-section">
              <h4 className="section-title">
                <Clock size={14} />
                최근 활동
              </h4>
              <div className="activity-list">
                {detail.recentActivity.slice(0, 5).map((activity, idx) => (
                  <div key={idx} className="activity-item">
                    <span className="activity-time">
                      {formatTime(activity.timestamp)}
                    </span>
                    <span className="activity-action">{activity.action}</span>
                    <span className="activity-details">{activity.details}</span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
        
        {/* 액션 버튼 */}
        <div className="panel-actions">
          {node.data.contractId && (
            <Link 
              to={`/contracts/${node.type.toUpperCase()}/${encodeURIComponent(node.data.contractId)}`}
              className="action-btn primary"
            >
              <ExternalLink size={14} />
              Contract 보기
            </Link>
          )}
          <Link 
            to={`/pipeline?entity=${node.data.entityType}`}
            className="action-btn secondary"
          >
            Pipeline 보기
          </Link>
        </div>
      </motion.div>
    </AnimatePresence>
  )
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}

function formatTime(timestamp: string): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '방금 전'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}분 전`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}시간 전`
  return date.toLocaleDateString()
}
```

---

### FE Phase 5: 라우팅 & Export (Day 5)

#### 5.1 라우팅 추가

```tsx
// app/routes/AppRoutes.tsx에 추가
import { Workflow } from '@/features/workflow'

// Routes 내부에 추가
<Route path="/workflow" element={<Workflow />} />
```

#### 5.2 사이드바 메뉴 추가

```tsx
// widgets/layout/ui/Layout.tsx의 navItems에 추가
{
  path: '/workflow',
  label: 'Workflow',
  icon: GitMerge  // lucide-react에서 import
}
```

#### 5.3 Feature Export

```typescript
// features/workflow/index.ts
export { Workflow } from './ui/Workflow'
export type * from './model/types'
```

---

## 📊 마일스톤 & 체크리스트

### BE 체크리스트 (3일)

#### Day 1: 도메인 모델
- [ ] WorkflowNode, WorkflowEdge, WorkflowGraph 모델 생성
- [ ] DTO 클래스 정의
- [ ] 포트 인터페이스 정의

#### Day 2: 서비스 & 그래프 빌더
- [ ] WorkflowService 구현
- [ ] WorkflowGraphBuilder 구현 (레이아웃 포함)
- [ ] 상태 및 통계 수집 로직

#### Day 3: API & 통합
- [ ] WorkflowRoutes 구현
- [ ] WorkflowHandler 구현
- [ ] AdminModule 등록
- [ ] API 테스트

### FE 체크리스트 (5일)

#### Day 1: 설정 & 구조
- [ ] @xyflow/react, dagre 설치
- [ ] 파일 구조 생성
- [ ] 타입 & 상수 정의
- [ ] API 클라이언트

#### Day 2: 커스텀 노드
- [ ] BaseNode 컴포넌트
- [ ] RawDataNode, SliceNode, ViewNode, SinkNode
- [ ] RuleNode (작은 규칙 노드)
- [ ] 노드 CSS 스타일링

#### Day 3: 캔버스 & 메인
- [ ] WorkflowCanvas (React Flow)
- [ ] Workflow 페이지
- [ ] 필터 & 툴바
- [ ] 상태 요약

#### Day 4: 상세 패널
- [ ] WorkflowDetailPanel
- [ ] 통계 표시
- [ ] Contract 링크
- [ ] 활동 로그

#### Day 5: 통합 & 폴리시
- [ ] 라우팅 추가
- [ ] 사이드바 메뉴
- [ ] 반응형 처리
- [ ] 최종 테스트

---

## 🎯 성공 기준

| 항목 | 목표 | 측정 방법 |
|------|------|----------|
| **렌더링 성능** | 100+ 노드에서 60fps | Chrome DevTools |
| **초기 로딩** | < 2초 | Network 탭 |
| **인터랙션 지연** | < 100ms | User timing |
| **사용자 만족도** | 90%+ | 피드백 수집 |

## 6. 기술적 고려사항

### 6.1 성능 최적화

```typescript
// 1. 노드 메모이제이션
const MemoizedNode = memo(BaseNode, (prev, next) => {
  return prev.data.status === next.data.status &&
         prev.data.stats?.throughput === next.data.stats?.throughput
})

// 2. 대량 노드 처리를 위한 가상화
const onlyVisibleNodes = useMemo(() => {
  return nodes.filter(node => isInViewport(node, viewport))
}, [nodes, viewport])

// 3. 상태 업데이트 디바운싱
const debouncedUpdate = useDebouncedCallback(
  (nodeId: string, status: NodeStatus) => {
    updateNodeStatus(nodeId, status)
  },
  500
)

// 4. 백엔드 캐싱
@Cacheable("workflow-graph", ttl = 10.seconds)
suspend fun getGraph(entityType: String?): WorkflowGraph

// 5. 증분 업데이트 (WebSocket 고려)
useEffect(() => {
  const ws = new WebSocket('/api/workflow/stream')
  ws.onmessage = (event) => {
    const update = JSON.parse(event.data)
    updateNodeStatus(update.nodeId, update.status)
  }
  return () => ws.close()
}, [])
```

### 6.2 접근성 (A11y)

```tsx
// 키보드 네비게이션
<ReactFlow
  nodesFocusable
  edgesFocusable
  onKeyDown={(e) => {
    if (e.key === 'Tab') navigateToNextNode()
    if (e.key === 'Enter') openNodeDetail()
    if (e.key === 'Escape') closePanel()
  }}
/>

// ARIA 레이블
<div
  role="application"
  aria-label="Workflow Canvas"
  aria-describedby="canvas-description"
>
  <span id="canvas-description" className="sr-only">
    데이터 파이프라인 시각화. 노드를 클릭하여 상세 정보 확인.
  </span>
</div>
```

### 6.3 반응형 디자인

```css
/* 태블릿 */
@media (max-width: 1024px) {
  .workflow-content {
    flex-direction: column;
  }
  
  .detail-panel {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 50vh;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  }
}

/* 모바일 */
@media (max-width: 768px) {
  .workflow-minimap { display: none; }
  .workflow-legend { display: none; }
  
  .detail-panel {
    height: 70vh;
  }
  
  .workflow-toolbar {
    flex-wrap: wrap;
    gap: 0.5rem;
  }
}
```

### 6.4 에러 처리

```typescript
// API 에러 처리
const { data, error, isError } = useQuery({
  queryKey: ['workflow-graph'],
  queryFn: fetchWorkflowGraph,
  retry: 3,
  retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 10000)
})

// 에러 UI
{isError && (
  <div className="error-overlay">
    <AlertCircle size={48} />
    <h3>워크플로우를 불러올 수 없습니다</h3>
    <p>{error.message}</p>
    <button onClick={() => refetch()}>다시 시도</button>
  </div>
)}

// 노드 에러 상태
const ErrorNode = ({ data }) => (
  <div className="node error">
    <AlertCircle className="error-icon" />
    <span>{data.label}</span>
    <span className="error-count">{data.stats?.errorCount} errors</span>
  </div>
)
```

## 7. 예상 결과

### 7.1 Before

- 개별 페이지에서 Contract, Pipeline, Outbox 따로 확인
- 데이터 흐름 파악을 위해 여러 페이지 이동 필요
- 전체 시스템 구조 이해 어려움

### 7.2 After

- 단일 캔버스에서 전체 데이터 흐름 확인
- 클릭 한 번으로 상세 정보 접근
- 문제 발생 시 즉시 위치 파악 (빨간 노드)
- 시스템 구조 직관적 이해

## 8. 향후 확장

1. **시간 여행**: 특정 시점의 파이프라인 상태 재현
2. **비교 뷰**: 두 시점/환경 간 차이 비교
3. **알림 연동**: 에러 노드 클릭 시 알림 설정
4. **내보내기**: 그래프를 이미지/PDF로 저장
5. **공유**: URL로 특정 뷰 공유

## 9. 참고

- [React Flow 문서](https://reactflow.dev)
- [D3.js Force Layout](https://d3js.org/d3-force)
- [Dagre 레이아웃 알고리즘](https://github.com/dagrejs/dagre)
