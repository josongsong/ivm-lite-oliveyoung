# SDK 단순화 — 단계별 제어 제거 (2026-02-12)

## 🎯 목표

**SDK에서 단계별 제어 제거 및 올인원 처리로 단순화**

기존: `ingestOnly`, `compileOnly`, `shipOnly` 세분화
신규: **Raw → Slicing → View → Sink 자동 처리** (IngestionOrchestrator 기반)

---

## 배경

### AS-IS (복잡한 단계별 제어)

```kotlin
// SDK 사용 예시 (기존)
Ivm.deploy {
    product(id = "123", name = "Product A") {
        ingestOnly()  // ❌ 불필요한 복잡도
    }
}

Ivm.deploy {
    product(id = "123") {
        compileOnly()  // ❌ 불필요한 복잡도
    }
}

Ivm.deploy {
    product(id = "123") {
        ship {
            opensearch()  // ❌ 불필요한 복잡도
        }
    }
}
```

**문제점**:
- 사용자가 단계를 직접 제어해야 함
- 복잡도 증가 (3단계 분리)
- IngestionOrchestrator는 이미 올인원 처리
- DynamoDB Streams가 자동으로 Sink 처리

---

### TO-BE (올인원 처리)

```kotlin
// SDK 사용 예시 (신규)
Ivm.deploy {
    product(id = "123", name = "Product A")
    // ✅ Raw → Slicing → View → SinkEvent까지 자동 처리
}
```

**장점**:
- ✅ **단순화**: 한 줄로 처리 완료
- ✅ **자동 Sink**: DynamoDB Streams → Lambda → S3/OpenSearch
- ✅ **트랜잭션 보장**: IngestionOrchestrator 단일 트랜잭션
- ✅ **사용자 편의성**: 복잡한 제어 불필요

---

## 제거 대상 코드

### 1. CompileMode (단계별 제어)

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/model/CompileMode.kt`

```kotlin
sealed interface CompileMode {
    data object Sync : CompileMode      // ❌ 제거 대상
    data object Async : CompileMode     // ❌ 제거 대상
    data object Skip : CompileMode      // ❌ 제거 대상 (ingestOnly)
}
```

**이유**: IngestionOrchestrator가 Slicing까지 자동 처리

---

### 2. ShipMode (단계별 제어)

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/model/ShipMode.kt`

```kotlin
sealed interface ShipMode {
    data object Sync : ShipMode         // ❌ 제거 대상
    data object Async : ShipMode        // ❌ 제거 대상
}
```

**이유**: DynamoDB Streams가 자동으로 Sink 처리

**Note**: 이미 `@Deprecated` 표시됨

---

### 3. DeploySpec 단계별 옵션

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/model/DeploySpec.kt`

```kotlin
data class DeploySpec(
    val compileMode: CompileMode = CompileMode.Sync,  // ❌ 제거
    val shipSpec: ShipSpec? = null,                   // ❌ 제거
    val cutoverMode: CutoverMode = CutoverMode.Skip   // ✅ 유지 (버전 전환용)
)
```

**변경 후**:
```kotlin
data class DeploySpec(
    val cutoverMode: CutoverMode = CutoverMode.Skip   // ✅ 유지 (버전 전환용)
)
```

---

### 4. DSL Accessors (단계별 제어)

#### CompileAccessor ❌ 제거
**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/dsl/deploy/CompileAccessor.kt`

```kotlin
interface CompileAccessor {
    fun compileSync()    // ❌ 제거
    fun compileAsync()   // ❌ 제거
    fun ingestOnly()     // ❌ 제거 (Skip과 동일)
}
```

#### ShipAccessor ❌ 제거
**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/dsl/deploy/ShipAccessor.kt`

```kotlin
interface ShipAccessor {
    fun ship(block: SinkBuilder.() -> Unit)  // ❌ 제거
}
```

#### ShipAsyncOnlyAccessor ❌ 제거
**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/dsl/deploy/ShipAsyncOnlyAccessor.kt`

```kotlin
interface ShipAsyncOnlyAccessor {
    fun ship(block: SinkBuilder.() -> Unit)  // ❌ 제거
}
```

---

### 5. DeployExecutor 단계별 로직

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/execution/DeployExecutor.kt`

```kotlin
// 기존 복잡한 로직
when (spec.compileMode) {
    is CompileMode.Sync -> { /* 동기 Compile */ }
    is CompileMode.Async -> { /* 비동기 Compile */ }
    is CompileMode.Skip -> { /* Ingest만 */ }
}

when (spec.shipSpec) {
    is ShipSpec.Sync -> { /* 동기 Ship */ }
    is ShipSpec.Async -> { /* 비동기 Ship */ }
    null -> { /* Ship 스킵 */ }
}
```

**변경 후**:
```kotlin
// 단순화된 로직
suspend fun <T : EntityInput> execute(input: T): DeployResult {
    // IngestionOrchestrator 호출 (Raw → Slicing → View → SinkEvent)
    val result = orchestrator.ingest(command)
    // DynamoDB Streams가 자동으로 Sink 처리
}
```

---

## 유지 대상 코드

### 1. CutoverMode ✅ 유지

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/model/CutoverMode.kt`

```kotlin
sealed interface CutoverMode {
    data object Skip : CutoverMode          // ✅ 유지
    data object Immediate : CutoverMode     // ✅ 유지
    data object Scheduled : CutoverMode     // ✅ 유지
}
```

**이유**: 버전 전환(Cutover)은 별도 비즈니스 로직

---

### 2. SinkBuilder ✅ 유지 (설정 목적)

**파일**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/dsl/sink/SinkBuilder.kt`

```kotlin
class SinkBuilder {
    fun opensearch(block: OpenSearchBuilder.() -> Unit)
    fun personalize(block: PersonalizeBuilder.() -> Unit)
}
```

**변경 용도**: Sink 타겟 설정 (SinkRule 등록)
- 현재: Ship 트리거용 ❌
- 신규: SinkRule 설정용 ✅

---

## 마이그레이션 가이드

### Step 1: 기존 코드 → 신규 코드

#### Before (복잡)
```kotlin
Ivm.deploy {
    product(id = "123", name = "Product A") {
        compileSync()
        ship {
            opensearch {
                indexName = "products"
            }
        }
    }
}
```

#### After (단순)
```kotlin
Ivm.deploy {
    product(id = "123", name = "Product A")
    // ✅ Raw → Slicing → View → SinkEvent 자동 처리
    // ✅ DynamoDB Streams → Lambda → OpenSearch
}
```

---

### Step 2: SinkRule 설정 (별도)

```kotlin
// Sink 타겟 설정 (SinkRule 등록)
Ivm.configureSink {
    forEntity("product") {
        opensearch {
            indexName = "products"
            routing = "productId"
        }
    }
}
```

**Note**: SinkRule은 계약(Contract)으로 관리되므로 별도 API

---

## 구현 계획

### Phase 1: LEGACY 표시 (즉시)

- [x] `CompileMode` → `@Deprecated`
- [x] `ShipMode` → `@Deprecated` (이미 완료)
- [ ] `CompileAccessor` → `@Deprecated`
- [ ] `ShipAccessor` → `@Deprecated`
- [ ] `ShipAsyncOnlyAccessor` → `@Deprecated`

---

### Phase 2: DeployExecutor 단순화 (즉시)

- [ ] `executeSync()` → `execute()`로 단순화
- [ ] CompileMode 분기 제거
- [ ] ShipMode 분기 제거
- [ ] IngestionOrchestrator 호출로 통합

---

### Phase 3: 코드 제거 (6개월 후)

- [ ] CompileMode.kt 삭제
- [ ] ShipMode.kt 삭제 (이미 Deprecated)
- [ ] CompileAccessor.kt 삭제
- [ ] ShipAccessor.kt 삭제
- [ ] ShipAsyncOnlyAccessor.kt 삭제
- [ ] 관련 테스트 삭제

---

## 영향 범위

### 1. SDK 사용자 (Breaking Change)

**기존 코드**:
```kotlin
product(id = "123") {
    compileSync()
    ship { opensearch() }
}
```

**마이그레이션**:
```kotlin
product(id = "123")
// ✅ 단순화: 모든 처리 자동
```

---

### 2. Admin UI (영향 없음)

Admin UI는 HTTP API 직접 호출
- POST /api/v1/ingest → IngestionOrchestrator
- SDK 변경 영향 없음

---

### 3. 테스트 (수정 필요)

- `CompileAccessorTest.kt` → 제거
- `ShipAccessorTest.kt` → 제거
- `DeployExecutorTest.kt` → 단순화된 로직 반영

---

## 검증 체크리스트

- [ ] DeployExecutor 단순화 완료
- [ ] LEGACY 표시 추가
- [ ] 기존 테스트 수정
- [ ] 통합 테스트 (E2E) 통과
- [ ] 문서 업데이트
- [ ] 마이그레이션 가이드 작성

---

## 참고 문서

- [IngestionOrchestrator](../src/main/kotlin/com/oliveyoung/ivmlite/pkg/rawdata/application/IngestionOrchestrator.kt)
- [DynamoDB Streams 아키텍처](./DYNAMODB-STREAMS-FINAL.md)
- [SDK 아키텍처](./SDK-ARCHITECTURE.md)

---

## 결론

**SDK 단순화로 사용자 편의성 극대화**

✅ **단계별 제어 제거**: `ingestOnly`, `compileOnly`, `shipOnly` 제거
✅ **올인원 처리**: Raw → Slicing → View → SinkEvent 자동
✅ **DynamoDB Streams**: 자동 Sink 처리
✅ **사용자 경험**: 한 줄로 처리 완료

**SOTA급 SDK 완성!** 🚀
