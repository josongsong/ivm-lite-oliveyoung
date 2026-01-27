# ADR-0007: IVM Sink Orchestration, Triggering, and Plugin-based Delivery

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-007

---

## Context

Slice/View 결과를 정책 기반(SinkRule)으로 외부 시스템(OpenSearch, Reco 등)에 전달하는 Sink Orchestration 아키텍처가 필요했습니다.

요구사항:
- Sink는 pull 스캐너가 아님
- Sink는 항상 트리거 기반(Task 기반)으로 동작
- Slice/View를 Port를 통해 읽어서 외부로 전달
- v0는 sync-first로 API 요청이 곧 트리거
- v1+는 outbox/eventbus를 트리거로 하되 동일 SinkOrchestrator + Plugin이 실행

## Decision

**IVM Sink Orchestration, Triggering, and Plugin-based Delivery** 아키텍처를 채택합니다.

### 핵심 원칙

1. **Sink는 항상 트리거 기반(Task 기반)으로만 동작**
   - Sink가 주기적으로 slice를 스캔 ❌
   - Slice 저장소를 직접 쿼리 ❌
   - 트리거 기반 실행만 허용 ⭕

2. **Slice/View 데이터는 outbox/이벤트에 실리지 않는다**
   - SinkPlugin은 Port를 통해 Slice/View를 읽는다
   - Outbox/EventBus에는 참조만 저장 ⭕
   - 실데이터 넣기 금지 ❌

3. **Sync / Outbox / EventBus subscriber는 트리거 방식의 차이일 뿐이며, 실행은 동일 SinkOrchestrator + SinkPlugin으로 수행**

### 트리거 모델

#### Sync-first (v0)

- **트리거 주체**: apps/runtimeapi
- **트리거 방식**: API 호출
- **실행 주체**: runtimeapi 프로세스
- **실행 흐름**: API 요청 → Slice/View 생성 → SinkOrchestrator.run(SinkPlan) → SinkPlugin이 Slice/View를 읽어 OpenSearch/Reco로 전달

#### Outbox 기반 Async (v1+)

- **트리거 주체**: outbox 이벤트
- **실행 주체**: apps/sinkworker
- **역할 분리**: runtimeapi는 TaskSpec + SinkPlanRef를 outbox에 적재, sinkworker는 outbox consume 후 SinkOrchestrator 실행

#### Event Bus 기반 Subscriber (v1+, 확장 가능)

- **트리거 주체**: Event Bus (예: Kafka, SNS/SQS)
- **실행 주체**: 독립 subscriber 프로세스
- **역할 분리**: runtimeapi는 Slice build 완료 시 이벤트 발행, subscriber는 consume → SinkOrchestrator 실행

### "Slice에 있는 걸 누가, 어떻게 읽는가"

**읽는 주체**: SinkPlugin (예: opensearch-indexer@1, personalize-feed-publisher@1)

**읽는 방법**: 반드시 Port 계약 사용 ⭕
- 직접 DB 접근 ❌
- `SliceReaderPort` 또는 `SliceBatchReaderPort` 사용
- View의 경우 `SliceBatchReaderPort` 사용

**읽기 방식**: 배치/스트리밍 방식, cursor 기반 페이지네이션

### SinkRule / SinkPlan / Plugin 구조

#### SinkRule (정책, Contract Registry 저장)

어떤 입력을, 어떤 타겟으로, 어떤 매핑/전달/커밋 규칙으로 보낼지 정의:

```yaml
id: sinkrule.opensearch.product-search
version: 1.0.0
status: ACTIVE

input:
  type: SLICE
  sliceTypes: [PRODUCT_CORE, PRODUCT_DISCOVERY]

target:
  type: OPENSEARCH
  index: "product-search-global"
  alias: "product-search-active"

mapping:
  docIdSpec:
    scope: SLICE
    pattern: "{entityKey}#{version}"
  fields:
    - from: "CORE.title"
      to: "title"
```

#### SinkPlan

단일 Task에서 실행할 SinkRule들의 총순서

#### SinkPlugin

SinkRule을 실제로 수행하는 실행체
- 입력/출력 계약 고정
- Plugin은 Contract Registry에 등록됨
- Plugin 실행은 결정적이어야 함

### OpenSearch SinkPlugin (예시)

**책임**:
- Slice/View를 읽는다 (SliceReaderPort/SliceBatchReaderPort/ViewReaderPort 사용)
- Mapping DSL 적용
- Bulk upsert 수행
- Alias swap 등 커밋 정책 수행

**내부 파이프라인**:
1. ResolveInput (SliceReaderPort 또는 SliceBatchReaderPort)
2. Transform (mapping DSL, RFC-002: Canonicalization 규칙 준수)
3. Bulk Upsert (OpenSearch, doc_id 기반 멱등성 보장)
4. Commit (alias swap or upsert)
5. CommitRef 생성

**CommitRef**:
```json
{
  "type": "opensearch",
  "index": "search-global-3",
  "alias": "search-active-global",
  "docCount": 120341,
  "taskId": "task_...",
  "commitHash": "sha256:...",
  "docIds": ["entityKey1#v1", "entityKey2#v2"]
}
```

### Domain-sliced 구조

```
sink/
  domain/
    SinkRuleV1.kt
    SinkPlanV1.kt
    PluginDescriptorV1.kt
    CommitRefV1.kt
    SinkOrchestrator.kt
  application/
    SinkOrchestratorUseCase.kt
    SinkPlanCompiler.kt
    PluginRouter.kt
  ports/
    SinkRuleRegistryPort.kt
    PluginRegistryPort.kt
    SinkPluginPort.kt
    SliceReaderPort.kt
    SliceBatchReaderPort.kt
    ViewReaderPort.kt
  adapters/
    registry/
      SinkRuleRegistryAdapter.kt
      PluginRegistryAdapter.kt
    plugins/
      opensearch/
        OpenSearchIndexerPlugin.kt
      personalize/
        PersonalizeFeedPublisherPlugin.kt
```

### 절대 금지 패턴 (P0)

- Sink가 주기적으로 slice를 스캔 ❌
- Slice 저장소를 직접 쿼리 ❌
- outbox/eventbus에 실데이터 적재 ❌
- Sink 로직을 API 레이어에 구현 ❌
- 트리거 계층에서 비즈니스 로직 구현 ❌

### 허용 패턴

- 트리거 기반 실행만 허용 ⭕
- Port를 통한 Slice/View 읽기만 허용 ⭕
- Outbox/EventBus에는 참조만 저장 ⭕
- SinkPlugin은 독립 도메인으로 분리 ⭕
- 트리거 방식(sync/outbox/eventbus) 교체 가능 ⭕
- Subscriber는 "받아서 run"만 수행 ⭕

## Consequences

### Positive

- ✅ Sink는 항상 트리거 기반으로만 동작하여 결정성 보장
- ✅ Slice/View는 Port를 통해 읽어서 도메인 경계 유지
- ✅ Sync / Outbox / EventBus subscriber는 트리거만 다르고 실행 모델은 동일
- ✅ SinkPlugin은 독립 도메인으로 분리되어 확장성 향상
- ✅ 설계상 확장성은 열려있음 (나중에 비동기 subscriber가 처리하는 형태로 자연스럽게 확장 가능)

### Negative

- ⚠️ 트리거 기반 설계로 인한 복잡도 증가
- ⚠️ Port를 통한 접근으로 인한 간접성
- ⚠️ SinkPlugin 개발 및 관리 오버헤드

### Neutral

- 트리거 처리 비용
- Plugin 실행 오버헤드

---

## 참고

- [RFC-V4-007](../rfc/rfc007.md) - 원본 RFC 문서
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
- [RFC-V4-005](../rfc/rfc005.md) - Domain-sliced Architecture
