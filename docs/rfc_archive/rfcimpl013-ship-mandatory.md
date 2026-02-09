# RFC-IMPL-013: SinkRule 기반 자동 Ship (Automatic Ship via SinkRule)

## Status: Implemented

## Executive Summary

Slice 생성 시 SinkRule 기반으로 **자동으로** ShipRequested outbox가 생성됩니다.
SDK에서 매번 `ship.to { opensearch() }` 호출할 필요 없습니다.

```kotlin
// 이게 전부!
ivm.product(product).deploy()
// → Slice 생성 → SinkRule 매칭 → ShipRequested 자동 생성 → Sink 전달
```

## 배경

### 문제 1: 매번 ship 설정 필요

```kotlin
// ❌ 매번 ship 설정해야 함
ivm.product(product).deploy {
    compile.sync()
    ship.to { opensearch() }  // 매번?
}
```

### 문제 2: Ship 누락 리스크

개발자가 ship 설정을 깜빡하면 Slice만 생성되고 Sink로 전달 안됨.

### 문제 3: ship.sync() 복잡성

ship.sync()와 ship.async() 두 가지 경로로 인한 복잡성.

## 해결 방안

### 1. SinkRule 기반 자동 Ship

Contract YAML에 SinkRule 정의:

```yaml
# sinkrule-opensearch-product.v1.yaml
kind: SINKRULE
id: sinkrule.opensearch.product
input:
  entityTypes: [PRODUCT, BRAND, CATEGORY]
  sliceTypes: [CORE]
target:
  type: OPENSEARCH
  endpoint: ${OPENSEARCH_ENDPOINT}
  indexPattern: "ivm-products-{tenantId}"
```

**Slice 생성 시 자동으로 매칭되는 SinkRule 찾아서 ShipRequested 생성!**

### 2. SDK 단순화

```kotlin
// 기본: SinkRule 기반 자동 Ship
ivm.product(product).deploy()

// 특정 sink로 override하고 싶을 때만
ivm.product(product).deploy {
    ship.to { personalize() }  // SinkRule 대신 personalize로
}

// Ship 완전 비활성화
ivm.product(product).compileOnly()
```

### 3. ship.sync() 제거

모든 ship은 outbox를 통해 비동기 처리.

## 동작 흐름

```
ivm.product(product).deploy()
  ↓
IngestWorkflow
  → RawData 저장
  → RawDataIngested outbox 생성
  ↓
OutboxPollingWorker (RawDataIngested 처리)
  → SlicingWorkflow.executeAuto()
  → Slice 저장
  → SinkRuleRegistry.findByEntityType("PRODUCT")  ← 자동!
  → ShipRequested outbox 생성 (매칭되는 SinkRule마다)
  ↓
OutboxPollingWorker (ShipRequested 처리)
  → ShipEventHandler
  → ShipWorkflow
  → Sink (OpenSearch, Personalize, etc.)
```

## 파일 변경

### 새로 추가

| 파일 | 설명 |
|------|------|
| `pkg/sinks/domain/SinkRule.kt` | SinkRule 도메인 모델 |
| `pkg/sinks/ports/SinkRuleRegistryPort.kt` | SinkRule 조회 포트 |
| `pkg/sinks/adapters/InMemorySinkRuleRegistry.kt` | 개발/테스트용 구현체 |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `OutboxPollingWorker.kt` | Slicing 완료 후 자동 ShipRequested 생성 |
| `DeployBuilder.kt` | ship 선택적으로 변경 (SinkRule 기반 자동) |
| `DeployableContext.kt` | deploy() 블록 없이 호출 가능 |
| `ShipMode.kt` | Sync deprecated |
| `ShipAccessor.kt` | sync() 제거, to() 추가 |

## API 변경

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| `deploy()` | ship 필수 | ship 선택적 (SinkRule 자동) |
| `deploy { }` | ship.to { } 필수 | ship.to { } 선택적 (override용) |
| `compileOnly()` | - | Ship 완전 비활성화 |
| `ship.sync()` | 사용 가능 | ❌ 제거 |
| `ship.to()` | - | ✅ 추가 (override용) |

## SOTA 포인트

1. **Zero Config Ship**: SinkRule만 정의하면 deploy()만 호출
2. **Automatic Routing**: entityType + sliceType으로 자동 라우팅
3. **Multi-Sink**: 하나의 Slice가 여러 Sink로 동시 전송 가능
4. **Outbox 일관성**: 모든 ship은 outbox 경유 → 장애 복구 가능
5. **Override 가능**: 필요시 특정 sink로 명시적 전송

## 사용 예시

```kotlin
// 1. 기본 (권장) - SinkRule 자동
ivm.product(product).deploy()

// 2. compile 모드 설정
ivm.product(product).deploy {
    compile.async()
}

// 3. 특정 sink로 override
ivm.product(product).deploy {
    ship.to { personalize() }
}

// 4. Ship 비활성화
ivm.product(product).compileOnly()
```
