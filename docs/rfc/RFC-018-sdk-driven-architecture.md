# RFC-018: SDK-Driven Architecture (Outbox 제거)

**Status**: Draft
**Created**: 2026-02-12
**Author**: Claude + User

---

## 요약

Outbox 패턴을 제거하고 SDK가 전체 흐름을 제어하는 아키텍처로 전환합니다.

**핵심 변경**:
- Outbox 테이블 + Worker 제거
- 엔진은 Stateless API만 제공
- SDK가 RawData → Slicing → View → Sink 흐름 제어
- 재시도 정책을 SDK에서 제공 (사용자 제어 가능)

---

## 동기

### 현재 문제 (Outbox 기반)

1. **운영 복잡도 높음**
   - OutboxPollingWorker 모니터링 필요
   - Stale Entry 복구 로직
   - Worker 장애 시 재시작 필요

2. **폴링 오버헤드**
   - 지속적인 SELECT 쿼리 (DB 부하)
   - 폴링 간격 튜닝 필요 (100ms~1s)

3. **제어권 불명확**
   - 사용자는 API만 호출, 이후 흐름은 블랙박스
   - 재시도 정책 커스터마이징 불가

### 제안 아키텍처 (SDK-Driven)

1. **엔진 단순화**
   - Outbox 없이 Stateless API만 제공
   - Worker 없음 (운영 부담 제거)

2. **SDK가 흐름 제어**
   - RawData → Slicing → View 흐름을 SDK가 순차 호출
   - 재시도 정책을 SDK에서 제공 (ExponentialBackoff 등)

3. **Sink 발송은 엔진 책임**
   - View 생성 시 자동으로 SQS 발송 (비동기)
   - SDK는 Sink 관여 안 함

---

## 아키텍처 비교

### Before (Outbox 기반)

```
사용자
  ↓ POST /raw-data
엔진: RawData 저장 + Outbox 생성
  ↓
OutboxPollingWorker (자동)
  ↓
SlicingWorkflow 실행
  ↓
View 생성 + Sink 발송
```

**문제점**:
- Worker 모니터링 필요
- 폴링 오버헤드
- 사용자 제어 불가

### After (SDK-Driven)

```
SDK (사용자 제어)
  ↓ 1. POST /raw-data
엔진: RawData 저장만
  ↓ 2. POST /slicing/trigger (SDK 호출)
엔진: Slicing 실행
  ↓ 3. POST /views/compose (SDK 호출)
엔진: View 생성 + Sink 발송 (자동)
```

**장점**:
- Worker 없음 (운영 간편)
- SDK가 재시도 제어
- 명시적 흐름

---

## 설계

### 1. 엔진 API (Stateless)

#### POST /api/v1/raw-data
```kotlin
suspend fun ingest(rawData: RawData): Result<Unit> {
    rawDataRepo.save(rawData)
    return Result.Ok(Unit)
}
```

#### POST /api/v1/slicing/trigger
```kotlin
suspend fun triggerSlicing(
    entityKey: String,
    version: Long,
    mode: SlicingMode = SlicingMode.AUTO
): Result<List<SliceKey>> {
    val slices = slicingWorkflow.execute(entityKey, version, mode)
    return Result.Ok(slices.map { it.toKey() })
}
```

#### POST /api/v1/views/compose
```kotlin
suspend fun composeView(
    slices: List<SliceKey>,
    viewDefId: String
): Result<ViewRecord> {
    val view = viewComposer.compose(slices, viewDefId)

    // Sink 발송 (비동기, 실패해도 View 생성은 성공)
    sinkDispatcher.dispatch(view)

    return Result.Ok(view)
}
```

### 2. SDK 구현

```kotlin
class IvmLiteClient(
    private val baseUrl: String,
    private val retryPolicy: RetryPolicy = ExponentialBackoff(maxRetries = 3)
) {

    /**
     * 전체 플로우 실행: RawData → Slicing → View
     */
    suspend fun processEntity(
        rawData: RawData,
        viewDefId: String,
        slicingMode: SlicingMode = SlicingMode.AUTO
    ): Result<ViewRecord> {
        return withRetry(retryPolicy) {
            // 1. RawData 저장
            ingestRawData(rawData).bind()

            // 2. Slicing 트리거
            val sliceKeys = triggerSlicing(
                rawData.entityKey,
                rawData.version,
                slicingMode
            ).bind()

            // 3. View 조합 (Sink 자동 발송)
            composeView(sliceKeys, viewDefId).bind()
        }
    }

    /**
     * RawData 저장
     */
    suspend fun ingestRawData(rawData: RawData): Result<Unit> {
        return httpClient.post("$baseUrl/api/v1/raw-data") {
            setBody(rawData)
        }.toResult()
    }

    /**
     * Slicing 트리거
     */
    suspend fun triggerSlicing(
        entityKey: String,
        version: Long,
        mode: SlicingMode = SlicingMode.AUTO
    ): Result<List<SliceKey>> {
        return httpClient.post("$baseUrl/api/v1/slicing/trigger") {
            setBody(SlicingRequest(entityKey, version, mode))
        }.toResult()
    }

    /**
     * View 조합
     */
    suspend fun composeView(
        sliceKeys: List<SliceKey>,
        viewDefId: String
    ): Result<ViewRecord> {
        return httpClient.post("$baseUrl/api/v1/views/compose") {
            setBody(ComposeRequest(sliceKeys, viewDefId))
        }.toResult()
    }
}
```

### 3. 재시도 정책 (SDK 제공)

```kotlin
interface RetryPolicy {
    fun shouldRetry(attempt: Int, error: Throwable): Boolean
    fun delayMs(attempt: Int): Long
}

class ExponentialBackoff(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 100,
    private val maxDelayMs: Long = 10000
) : RetryPolicy {
    override fun shouldRetry(attempt: Int, error: Throwable): Boolean {
        return attempt < maxRetries && error is RetryableException
    }

    override fun delayMs(attempt: Int): Long {
        val delay = baseDelayMs * (2.0.pow(attempt)).toLong()
        return min(delay, maxDelayMs)
    }
}

suspend fun <T> withRetry(
    policy: RetryPolicy,
    block: suspend () -> Result<T>
): Result<T> {
    var attempt = 0
    while (true) {
        val result = block()

        if (result is Result.Ok || !policy.shouldRetry(attempt, result.error)) {
            return result
        }

        delay(policy.delayMs(attempt))
        attempt++
    }
}
```

---

## 마이그레이션 계획

### Phase 1: Outbox 사용 중단 (호환성 유지)

1. **새 API 추가** (Outbox 없이 동작)
   - `POST /api/v1/slicing/trigger`
   - `POST /api/v1/views/compose`

2. **SDK 업데이트**
   - `IvmLiteClient.processEntity()` 구현
   - 재시도 정책 추가

3. **기존 Outbox API 유지** (Deprecated)
   - `POST /raw-data` (Outbox 생성)
   - Worker 계속 동작

**검증**:
```kotlin
// 기존 방식 (Outbox)
POST /raw-data
→ Worker가 자동 처리

// 새 방식 (SDK)
client.processEntity(rawData, viewDefId)
→ SDK가 명시적 호출

// 결과 동일한지 확인
```

### Phase 2: Outbox 제거

1. **Outbox 비활성화**
   ```yaml
   worker:
     enabled: false  # OutboxPollingWorker 중단
   ```

2. **모든 클라이언트 SDK 전환 확인**

3. **Outbox 코드 제거**
   - `OutboxEntry.kt`
   - `OutboxPollingWorker.kt`
   - `OutboxRepositoryPort.kt`
   - `OutboxRoutes.kt`
   - DB 마이그레이션 (테이블 유지, 나중에 DROP)

4. **테스트 수정**
   - Outbox 관련 테스트 제거
   - SDK 기반 E2E 테스트 추가

### Phase 3: DB 정리 (선택적)

```sql
-- Outbox 데이터 백업 (선택)
CREATE TABLE outbox_archive AS SELECT * FROM outbox;

-- Outbox 테이블 제거
DROP TABLE outbox CASCADE;
DROP TABLE outbox_stats CASCADE;
```

---

## 제거되는 컴포넌트

### 코드

```
src/main/kotlin/com/oliveyoung/ivmlite/pkg/rawdata/domain/
  ├── OutboxEntry.kt                         ❌ 제거
  └── OutboxPayload.kt                       ❌ 제거

src/main/kotlin/com/oliveyoung/ivmlite/pkg/rawdata/ports/
  └── OutboxRepositoryPort.kt                ❌ 제거

src/main/kotlin/com/oliveyoung/ivmlite/pkg/orchestration/application/
  └── OutboxPollingWorker.kt                 ❌ 제거

src/main/kotlin/com/oliveyoung/ivmlite/apps/runtimeapi/routes/
  └── OutboxRoutes.kt                        ❌ 제거

src/main/kotlin/com/oliveyoung/ivmlite/pkg/health/adapters/
  └── OutboxHealthCheck.kt                   ❌ 제거

src/main/kotlin/com/oliveyoung/ivmlite/shared/domain/types/
  └── OutboxStatus.kt                        ❌ 제거
```

### 테스트

```
src/test/kotlin/
  ├── com/oliveyoung/ivmlite/e2e/
  │   ├── OutboxTier1E2ETest.kt              ❌ 제거
  │   ├── OutboxStressTest.kt                ❌ 제거
  │   └── OutboxClaimE2ETest.kt              ❌ 제거
  ├── com/oliveyoung/ivmlite/unit/
  │   └── OutboxIdempotencyTest.kt           ❌ 제거
  └── com/oliveyoung/ivmlite/pkg/
      ├── rawdata/
      │   ├── OutboxTier1FeaturesTest.kt     ❌ 제거
      │   ├── OutboxRepositoryPortTest.kt    ❌ 제거
      │   ├── OutboxEntryTest.kt             ❌ 제거
      │   └── OutboxClaimConcurrencyTest.kt  ❌ 제거
      └── orchestration/
          └── OutboxPollingWorkerTest.kt     ❌ 제거
```

### DB 테이블 (Phase 3에서)

```sql
DROP TABLE outbox CASCADE;
DROP TABLE outbox_stats CASCADE;
```

---

## 트레이드오프

### ✅ 장점

1. **운영 부담 제거**
   - Worker 모니터링 불필요
   - Stale Entry 복구 로직 불필요
   - DB 폴링 부하 제거

2. **명시적 제어**
   - SDK가 전체 흐름 제어
   - 재시도 정책 커스터마이징 가능
   - 에러 처리 명확

3. **코드 단순화**
   - Outbox 관련 코드 ~3000 LOC 제거
   - 테스트 코드 간소화

### ⚠️ 단점

1. **SDK 의존성**
   - 사용자가 반드시 SDK 사용해야 함
   - Raw HTTP API만으로는 자동 처리 불가

2. **재시도 책임 이전**
   - 엔진이 자동 재시도하던 것을 SDK가 해야 함
   - 사용자 코드에서 재시도 처리 필요

3. **레이턴시 증가 가능**
   - 기존: RawData 저장만 응답 (빠름)
   - 신규: RawData → Slicing → View 전체 완료 (느림)
   - **해결**: SDK에서 비동기 처리 옵션 제공

---

## 호환성

### Breaking Changes

1. **Outbox API 제거** (Phase 2)
   - `GET /api/v1/outbox/status` → 삭제
   - `POST /api/v1/outbox/reset` → 삭제

2. **자동 Slicing 중단**
   - RawData 저장 후 자동 Slicing 안 됨
   - SDK가 명시적으로 `triggerSlicing()` 호출 필요

### 마이그레이션 가이드

```kotlin
// Before (Outbox 기반)
val client = HttpClient.newBuilder().build()
client.send(
    HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/raw-data"))
        .POST(...)
        .build()
)
// 이후 Worker가 자동으로 Slicing + View 생성

// After (SDK 기반)
val client = IvmLiteClient("http://localhost:8080")
client.processEntity(rawData, viewDefId)  // 명시적 흐름 제어
```

---

## 보안 고려사항

변경사항 없음 (API 인증/인가는 기존과 동일)

---

## 모니터링

### 제거되는 메트릭

- `outbox.pending.count` (Outbox PENDING 개수)
- `outbox.processing.count` (Outbox PROCESSING 개수)
- `outbox.failed.count` (Outbox FAILED 개수)
- `worker.poll.count` (Worker 폴링 횟수)
- `worker.processed.count` (Worker 처리 횟수)

### 추가되는 메트릭

- `sdk.processEntity.duration` (SDK 전체 플로우 소요 시간)
- `sdk.retry.count` (SDK 재시도 횟수)
- `api.slicing.trigger.count` (Slicing API 호출 횟수)
- `api.views.compose.count` (View Compose API 호출 횟수)

---

## 테스트 전략

### Phase 1 (호환성 검증)

```kotlin
@Test
fun `Outbox 방식과 SDK 방식 결과 동일`() {
    // Outbox 방식
    val outboxResult = runBlocking {
        httpClient.post("/raw-data") { setBody(rawData) }
        delay(5000)  // Worker 처리 대기
        httpClient.get("/views/${rawData.entityKey}")
    }

    // SDK 방식
    val sdkResult = runBlocking {
        client.processEntity(rawData, viewDefId)
    }

    outboxResult shouldBe sdkResult
}
```

### Phase 2 (Outbox 제거 후)

```kotlin
@Test
fun `SDK processEntity E2E 테스트`() {
    val rawData = RawData(...)
    val result = client.processEntity(rawData, "view-product-core")

    result.shouldBeInstanceOf<Result.Ok>()
    val view = result.value

    // View 검증
    view.viewType shouldBe "PRODUCT_CORE"
    view.data shouldContain "\"id\":\"${rawData.entityKey}\""

    // Sink 발송 검증 (SQS 메시지 확인)
    val messages = sqsClient.receiveMessage(queueUrl)
    messages.shouldNotBeEmpty()
}
```

---

## 디펜던시 분석

### RFC-017-SOTA와의 관계

**RFC-018은 RFC-017-SOTA와 독립적입니다:**

| RFC | 범위 | 충돌 여부 |
|-----|------|----------|
| **RFC-017-SOTA** | Sink Plugin 아키텍처 (SQS → Lambda → S3) | ❌ 충돌 없음 |
| **RFC-018** | Outbox 제거 (RawData → Slicing 플로우) | ❌ 충돌 없음 |

**이유**:
- RFC-017-SOTA: **View → Sink** 플로우 (ViewComposerWithSink → SQS)
- RFC-018: **RawData → Slicing** 플로우 (Outbox 제거)

**RFC-017-SOTA는 그대로 유지됩니다:**
```kotlin
// RFC-017-SOTA 유지 (View → Sink)
ViewComposerWithSink {
    val view = baseComposer.compose(slices, viewDefId)
    sinkDispatcher.dispatch(view)  // ✅ SQS 직접 발송 (변경 없음)
}
```

**RFC-018만 변경 (RawData → Slicing):**
```kotlin
// Before (Outbox)
POST /raw-data → Outbox → Worker → Slicing

// After (SDK)
SDK.processEntity() → POST /raw-data → POST /slicing/trigger → POST /views/compose
```

---

## 구현 체크리스트

### ✅ 사전 확인 완료 (RFC-017 완료)

RFC-017 완료로 RFC-018 즉시 시작 가능:

- [x] ✅ SinkDispatcher 구현
- [x] ✅ SqsSinkPublisher (SQS 발송)
- [x] ✅ S3SinkPlugin + Lambda Handler
- [x] ✅ ViewComposerWithSink (자동 Sink 발송)
- [x] ✅ Terraform Preview 환경
- [x] ✅ E2E 테스트 통과 (248ms)

**결론**: 모든 Sink 인프라 준비 완료 → RFC-018 Phase 1 즉시 시작 가능 ✅

---

### Phase 1: 새 API 추가 (즉시 시작 가능 ✅)

- [ ] `POST /api/v1/slicing/trigger` API 구현
- [ ] `POST /api/v1/views/compose` API 구현
  - [x] ViewComposerWithSink 통합 (RFC-017 완료)
  - [x] Sink 발송 자동화 (SinkDispatcher 완료)
- [ ] SDK `IvmLiteClient` 구현
  - [ ] `processEntity()` 메서드
  - [ ] `RetryPolicy` 인터페이스
  - [ ] `ExponentialBackoff` 구현
- [ ] SDK E2E 테스트 작성
  - [ ] RawData → Slicing → View 플로우
  - [ ] Sink 발송 검증 (SQS 메시지 확인)
- [ ] 기존 Outbox 방식과 결과 동일성 검증

### Phase 2: Outbox 제거

- [ ] Worker 비활성화 (`worker.enabled = false`)
- [ ] 모든 클라이언트 SDK 전환 확인
- [ ] Outbox 코드 제거
  - [ ] `OutboxEntry.kt`
  - [ ] `OutboxPollingWorker.kt`
  - [ ] `OutboxRepositoryPort.kt`
  - [ ] `OutboxRoutes.kt`
  - [ ] `OutboxHealthCheck.kt`
  - [ ] `OutboxStatus.kt`
- [ ] Outbox 테스트 제거 (~10개 파일)
- [ ] 메트릭 업데이트
- [ ] 문서 업데이트 (README, API 문서)

### Phase 3: DB 정리

- [ ] Outbox 데이터 백업 (선택)
- [ ] `DROP TABLE outbox CASCADE`
- [ ] `DROP TABLE outbox_stats CASCADE`
- [ ] jOOQ 코드 재생성

---

## 참고 문서

- **RFC-017-SOTA**: Sink Plugin Architecture (SOTA급 Sink 시스템) - **의존 관계**
- RFC-IMPL Phase B-2: Outbox Polling (기존 Outbox 설계) - **제거 대상**

### RFC 간 실행 순서

```
1. RFC-017-SOTA Phase 1 ✅ (완료)
   └─ sinks-contract + 기본 구조

2. RFC-017-SOTA Phase 2 ⏳ (필수 선행)
   └─ SinkLedger + S3 Plugin + Lambda Handler

3. RFC-018 Phase 1 (새 API + SDK)
   └─ RFC-017-SOTA의 SinkDispatcher 사용

4. RFC-018 Phase 2 (Outbox 제거)

5. RFC-018 Phase 3 (DB 정리)

6. RFC-017-SOTA Phase 3 (운영 도구)
```

**중요**: RFC-018은 RFC-017-SOTA Phase 2가 완료된 후에만 시작 가능합니다.

---

## 의사결정 기록

**Q**: Outbox 없이 재시도를 어떻게 보장하나?
**A**: SDK에서 `RetryPolicy`를 제공하여 사용자가 재시도 정책을 제어합니다.

**Q**: SDK 사용 안 하는 클라이언트는?
**A**: Raw HTTP API로 각 단계를 수동 호출할 수 있습니다. 재시도는 직접 구현 필요.

**Q**: Slicing 실패 시 RawData는 어떻게 되나?
**A**: RawData는 DB에 저장된 상태로 유지됩니다. SDK가 재시도하거나 수동으로 재실행 가능.

**Q**: Sink 발송 실패는?
**A**: SQS DLQ + Lambda 재시도로 처리합니다. (RFC-017 참조)

---

## 다음 단계

1. ✅ RFC 승인
2. Phase 1 구현 (새 API + SDK)
3. Phase 1 검증 (기존 방식과 동일성 확인)
4. Phase 2 구현 (Outbox 제거)
5. Phase 3 구현 (DB 정리)
