# VIEW_TO_SINK Backfill — 구현 계획

> Slice → View 조합 → Sink(OpenSearch) 벌크 인덱싱. 전체 데이터 reindex 지원.
>
> **결정**: 벌크용 Lambda 프로세서로 처리 (20만 건급). 실시간 Sink 버퍼링은 [sink-buffering-strategy.md](./sink-buffering-strategy.md) 참고.

---

## 0. 실행 아키텍처 (Admin vs Lambda)

### 0-1. 옵션 비교

| 구분 | Admin 앱 | Lambda 벌크 (권장) |
|------|----------|---------------------|
| **실행 위치** | Admin 프로세스 (8081) | Lambda |
| **타임아웃** | 없음 (서버 종료 시 중단) | 15분/배치 |
| **스케일** | 단일 스레드 | SQS 병렬 → 여러 Lambda 동시 |
| **트레이싱** | tracingModule | lambdaTracingModule (OTEL, X-Ray) |
| **Admin 부하** | 높음 (대량 처리 시 점유) | 없음 |
| **복잡도** | 낮음 | 중간 (SQS, 인프라) |

### 0-2. Lambda 벌크 권장 이유

1. **대량 처리**: 10만 건 이상 시 Admin 프로세스가 오래 점유됨. Lambda는 SQS로 배치 분산.
2. **트레이싱**: Lambda는 이미 `lambdaTracingModule` 사용. SinkStreamHandler와 동일 패턴으로 span/attribute 적용.
3. **일관성**: SinkStreamProcessor는 이미 Lambda에서 동작. Sink 호출 경로가 Lambda로 통일됨.
4. **관측성**: X-Ray/OTLP로 배치별 추적, Lambda 메트릭(ConcurrentExecutions, Duration) 활용.

### 0-3. Lambda 벌크 흐름

```
Admin API: Backfill Job 생성 (PostgreSQL)
    → resolveScope(entityKeys) → SQS에 배치 메시지 전송
    → 메시지: { jobId, batchId, entityKeys: [100개], viewId, target }
Lambda: SQS 배치 수신
    → Slice 조회 → View 조합 → SinkPlugin.executeBatch
    → span: backfill.view_to_sink.batch (jobId, batchId, count, duration_ms)
    → (선택) Admin API 콜백으로 Progress 업데이트
```

### 0-4. 트레이싱 (Lambda)

| Span | Attributes | 설명 |
|------|------------|------|
| `BackfillViewToSink.processBatch` | jobId, batchId, entityCount, successCount, failedCount, duration_ms | 배치 전체 |
| `BackfillViewToSink.resolveView` | entityKey, viewId | View 조합 (개별 또는 배치) |
| `BackfillViewToSink.sinkBatch` | target, payloadCount | SinkPlugin 호출 |

`lambdaTracingModule` + `TRACING_ENABLED` / `OTEL_EXPORTER_OTLP_ENDPOINT` / `USE_XRAY` 환경변수로 동작. SinkStreamHandler와 동일.

---

## 1. 구현 위치

### 1-1. Admin 기반 (단순)

| 구분 | 경로 | 역할 |
|------|------|------|
| **핵심 로직** | `pkg/backfill/adapters/DefaultBackfillExecutor.kt` | `VIEW_TO_SINK` 분기 추가, `reprocessViewToSink()` 구현 |
| **DI** | `apps/admin/wiring/AdminModule.kt` | `DefaultBackfillExecutor` 의존성 추가, `sinkPluginModule` 포함 |
| **Backfill 타입** | `pkg/backfill/domain/BackfillType.kt` | `VIEW_TO_SINK` 이미 정의됨 |
| **Scope/Config** | `pkg/backfill/domain/BackfillScope.kt` | `viewIds` 이미 있음 (선택) |

### 1-2. Lambda 기반 (권장)

| 구분 | 경로 | 역할 |
|------|------|------|
| **핵심 로직** | `pkg/backfill/adapters/ViewToSinkBatchProcessor.kt` | Slice → View → SinkPlugin (SinkStreamProcessor와 유사) |
| **Lambda 핸들러** | `apps/lambda/BackfillViewToSinkHandler.kt` | SQS 이벤트 수신, 배치 처리, 트레이싱 |
| **Admin 오케스트레이션** | `BackfillService` + 신규 | Job 생성 → resolveScope → SQS 전송 |
| **인프라** | SQS 큐 `ivm-backfill-view-to-sink` | 배치 메시지 수신 |

---

## 2. 의존성 추가

### 2-1. DefaultBackfillExecutor 신규 의존성

| 의존성 | 용도 | 제공 모듈 |
|--------|------|-----------|
| `SliceReaderPort` | Slice 조회 (getLatestVersion) | productionAdapterModule |
| `QueryViewWorkflow` | Slice → View 조합 | workflowModule |
| `SinkRuleRegistryPort` | entityType → Sink target 목록 | productionAdapterModule |
| `SinkPluginRegistryPort` | SinkPlugin 실행 | **sinkPluginModule** (신규 포함) |
| `ContractRegistryPort` | QueryViewWorkflow 내부 사용 | productionAdapterModule |

### 2-2. Admin 모듈에 sinkPluginModule 추가

- **파일**: `AdminModule.kt` → `adminAllModules`
- **내용**: `sinkPluginModule` 추가 (SinkPluginRegistryPort, SinkLedger, SinkFailureRepository)
- **주의**: `OPENSEARCH_ENDPOINT` 등 환경변수 필요. Admin 실행 시 동일 env 사용.

---

## 3. 작업 계획

### Phase 1: 의존성 및 DI (1일)

| # | 작업 | 파일 |
|---|------|------|
| 1.1 | `DefaultBackfillExecutor` 생성자에 SliceReaderPort, QueryViewWorkflow, SinkRuleRegistryPort, SinkPluginRegistryPort 추가 | `DefaultBackfillExecutor.kt` |
| 1.2 | `AdminModule.backfillModule`에서 `DefaultBackfillExecutor`에 새 의존성 주입 | `AdminModule.kt` |
| 1.3 | `adminAllModules`에 `sinkPluginModule` 추가 | `AdminModule.kt` |

### Phase 2: VIEW_TO_SINK 로직 (2일)

| # | 작업 | 상세 |
|---|------|------|
| 2.1 | `supportedTypes`에 `VIEW_TO_SINK` 추가 | `DefaultBackfillExecutor.kt` |
| 2.2 | `processEntity`에 `VIEW_TO_SINK` 분기 추가 | `reprocessViewToSink(entityKey, config)` 호출 |
| 2.3 | `reprocessViewToSink` 구현 | 아래 4단계 플로우 |
| 2.4 | `resolveScope` | VIEW_TO_SINK도 기존과 동일 (listRawData → entityKeys). Slice 없는 건 processEntity에서 skip |

**reprocessViewToSink 플로우**:

```
1. entityKey 파싱 → tenantId
2. sliceRepo.getLatestVersion(tenantId, entityKey) → (version, slices)
   - 없으면 skip (EntityProcessResult success=false)
3. viewId 결정: scope.viewIds?.firstOrNull() ?: "view.product.search.v1"
4. QueryViewWorkflow.execute(tenantId, viewId, entityKey, version) → ViewResponse
5. SinkRuleRegistry.findByEntityType(entityType) → targets
6. 각 target에 대해:
   - SinkPluginRegistry.resolve(target) → plugin
   - SinkPayload.V1 생성 (viewData = ViewResponse.data)
   - plugin.execute(payload) 또는 executeBatch
7. 성공/실패 결과 반환
```

### Phase 3: 배치 최적화 (선택, 1일)

| # | 작업 | 상세 |
|---|------|------|
| 3.1 | `processBatch` VIEW_TO_SINK 시 bulk 호출 | entityKeys 배치로 View 조회 → SinkPlugin.executeBatch 한 번에 전송 |
| 3.2 | Slice batch 조회 | getLatestVersion을 배치로 (또는 기존 유지) |

### Phase 4: 테스트 및 검증 (1일)

| # | 작업 |
|---|------|
| 4.1 | 단위 테스트: `DefaultBackfillExecutor` VIEW_TO_SINK 분기 |
| 4.2 | E2E: Admin API로 Backfill 생성 (type=VIEW_TO_SINK) → 실행 → OpenSearch 문서 확인 |
| 4.3 | Admin UI에서 VIEW_TO_SINK 타입 선택 가능 여부 확인 |

---

## 3-B. Lambda 기반 작업 계획 (권장)

### Phase L1: ViewToSinkBatchProcessor (1일)

| # | 작업 | 상세 |
|---|------|------|
| L1.1 | `ViewToSinkBatchProcessor` 생성 | Slice 조회 → View 조합 → SinkPayload 목록 생성 (SinkStreamProcessor 로직 재사용) |
| L1.2 | SinkPlugin.executeBatch 호출 | 배치 단위로 Sink 전송 |
| L1.3 | SinkLedger 연동 | tryStart/complete/fail (멱등성, Lambda와 동일) |

### Phase L2: BackfillViewToSinkHandler (1일)

| # | 작업 | 상세 |
|---|------|------|
| L2.1 | `BackfillViewToSinkHandler` 생성 | SQS 이벤트 수신, RequestHandler |
| L2.2 | Koin 모듈 | lambdaTracingModule, sinkPluginModule, productionAdapterModule(일부), workflowModule |
| L2.3 | 트레이싱 | `tracer.withSpan("BackfillViewToSink.processBatch", mapOf("jobId", "batchId", "entityCount", ...))` |

### Phase L3: Admin → SQS 오케스트레이션 (1일)

| # | 작업 | 상세 |
|---|------|------|
| L3.1 | BackfillService VIEW_TO_SINK 분기 | resolveScope → entityKeys를 배치(100개)로 나눔 → SQS 전송 |
| L3.2 | SQS 메시지 스키마 | `{ jobId, batchId, entityKeys, viewId, target }` |
| L3.3 | Progress 업데이트 | Lambda 완료 시 SNS/SQS 콜백 또는 DynamoDB 직접 업데이트 (선택) |

---

## 4. 상세 설계

### 4-1. viewId / viewType 매핑

| entityType | 기본 viewId | viewType (SinkPayload) |
|------------|-------------|-------------------------|
| PRODUCT | view.product.search.v1 | PRODUCT_SEARCH |
| 기타 | scope.viewIds 필수 | Contract에서 viewName |

### 4-2. Sink target 결정

- `SinkRuleRegistry.findByEntityType("PRODUCT")` → ACTIVE 규칙의 target 목록
- `target.type.toPluginId()` → "opensearch-sink" 등
- `SinkPluginRegistry.resolve("opensearch-sink")` → OpenSearchSinkPlugin

### 4-3. SinkPayload.V1 생성

```kotlin
SinkPayload.V1(
    correlationId = "backfill-${jobId}",
    timestamp = Instant.now().toString(),
    idempotencyKey = SinkPayload.generateIdempotencyKey(tenantId, entityKey, version, viewType, payloadDigest),
    orderingKey = SinkPayload.generateOrderingKey(tenantId, entityKey),
    payloadDigest = SinkPayload.computePayloadDigest(viewData),
    tenantId = tenantId,
    entityKey = entityKey,
    entityVersion = version,
    viewType = viewType,  // "PRODUCT_SEARCH"
    viewData = viewResponse.data,  // JsonObject
    metadata = mapOf("jobId" to jobId)
)
```

### 4-4. SinkLedger 고려

- Lambda의 SinkStreamProcessor는 SinkLedger로 멱등성 보장
- Backfill에서 동일 entityKey를 재전송 시: idempotencyKey가 같으면 ALREADY_PROCESSED 가능
- OpenSearch는 external versioning으로 구버전 자동 drop → Ledger SKIP 또는 Backfill 전용 Ledger 정책 검토

### 4-5. 트레이싱 상세 (Lambda)

```kotlin
// BackfillViewToSinkHandler.kt
tracer.withSpan(
    name = "BackfillViewToSink.processBatch",
    attributes = mapOf(
        "backfill.job_id" to jobId,
        "backfill.batch_id" to batchId,
        "backfill.entity_count" to entityKeys.size.toLong(),
        "backfill.view_id" to viewId,
        "backfill.target" to target,
    ),
) { span ->
    val result = processor.processBatch(...)
    span.setAttribute("backfill.success_count", result.succeeded.size.toLong())
    span.setAttribute("backfill.failed_count", result.failed.size.toLong())
    span.setAttribute("backfill.duration_ms", elapsedMs)
}
```

- `lambdaTracingModule`: `TRACING_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `USE_XRAY` 환경변수
- X-Ray 연동 시: traceId가 AWS X-Ray에 전파되어 Lambda ↔ Admin 간 분산 추적 가능

---

## 5. 체크리스트

**Admin 기반**
- [ ] DefaultBackfillExecutor VIEW_TO_SINK 지원
- [ ] Admin에 sinkPluginModule 포함

**Lambda 기반 (권장)**
- [ ] ViewToSinkBatchProcessor 구현
- [ ] BackfillViewToSinkHandler 구현 (트레이싱 포함)
- [ ] SQS 큐 생성 및 Admin → SQS 전송
- [ ] Lambda 배포 (sinkPluginModule, DynamoDB, Contract 접근)

**공통**
- [ ] Backfill API/UI에서 VIEW_TO_SINK 타입 선택
- [ ] 단위 테스트
- [ ] E2E 테스트 (OpenSearch 연동)
- [ ] opensearch-index-plan.md 11-4절 "미구현 시" 문구 제거

---

## 6. 참고

- `docs/guides/opensearch-index-plan.md` 11-4절: Backfill 인덱싱 갭
- `docs/guides/sink-buffering-strategy.md`: 실시간 Sink 버퍼링 (SQS Batch Window 등)
- `sinks-contract/SinkPayload.kt`: SinkPayload.V1 구조
- `apps/lambda/SinkStreamProcessor.kt`: SinkPayload → SinkPlugin 호출 패턴
