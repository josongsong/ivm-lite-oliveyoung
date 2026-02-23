# RFC-019: External SDK Integration for Async Processing

**작성일**: 2026-02-12
**상태**: DRAFT
**우선순위**: HIGH

---

## 1. 개요

외부 서비스가 IVM-Lite SDK를 통해 데이터 처리를 요청하고, 동기로 Slicing까지 완료한 후, Sink 전달은 비동기 Outbox Worker가 처리하는 아키텍처를 제안합니다.

**핵심 패턴**:
```
SDK → 동기 Slicing (RawData → Slices → Views) → 공용 Outbox 등록 → 비동기 Ship
```

---

## 2. 동기

### 문제점
- 외부 서비스가 OpenSearch/Personalize Sink로 직접 전송 시 복잡도 증가
- Ship 실패 시 재처리 로직을 각 서비스가 구현 필요
- 엔티티별 SinkRule 라우팅 로직 중복

### 해결책
- **SDK 제공**: REST API 래퍼 (Kotlin + TypeScript)
- **동기 처리**: RawData → Slicing → View (응답 타임아웃 내 완료)
- **비동기 처리**: ViewsComposed → SinkRule → ShipRequested → Ship
- **공용 Outbox**: 단일 `outbox` 테이블로 이벤트 관리
- **jobId 추적**: 외부 서비스의 jobId로 end-to-end 추적

---

## 3. 아키텍처

### 3.1 전체 데이터 흐름

```
┌─────────────────┐
│ 외부 서비스      │
│ (Worker 역할)   │
└────────┬────────┘
         │ 1. POST /api/v1/ingest (jobId 포함)
         ▼
┌─────────────────────────────────────────────────┐
│ IVM-Lite SDK (Kotlin/TypeScript)                │
│ - IngestionOrchestrator.ingest()                │
│ - 동기: RawData → Slicing → View                │
│ - Outbox 등록: ViewsComposed (jobId 포함)       │
└────────┬────────────────────────────────────────┘
         │ 2. Response (200 OK, version, sliceCount)
         ▼
┌─────────────────┐
│ 외부 서비스      │ ← 동기 응답 (3초 이내)
└─────────────────┘

         ┌─────────────────────────────────────────┐
         │ 공용 Outbox Table                        │
         │ - job_id: 외부 서비스 jobId              │
         │ - event_type: ViewsComposed              │
         │ - aggregate_id: tenantId__entityKey      │
         └─────────┬───────────────────────────────┘
                   │ 3. Outbox Polling Worker (비동기)
                   ▼
         ┌─────────────────────────────────────────┐
         │ ShipEventHandler                         │
         │ - processViewsComposed()                 │
         │ - SinkRule 조회 (entityType 기반)       │
         │ - ShipRequested Outbox 생성 (jobId 전파)│
         └─────────┬───────────────────────────────┘
                   │ 4. ShipRequested 처리
                   ▼
         ┌─────────────────────────────────────────┐
         │ ShipWorkflow                             │
         │ - Slice 조회 → Sink 전송                 │
         │ - OpenTelemetry traceId 전파             │
         └─────────┬───────────────────────────────┘
                   │ 5. 외부 Sink 전송
                   ▼
         ┌─────────────────────────────────────────┐
         │ OpenSearch / Personalize / Custom Sink  │
         └─────────────────────────────────────────┘
```

---

### 3.2 공용 Outbox 테이블 스키마

**단일 `outbox` 테이블로 통합**:

```sql
CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    job_id VARCHAR(255),              -- 외부 서비스 jobId (추적용)
    event_type VARCHAR(100) NOT NULL, -- ViewsComposed, ShipRequested, SliceUpdated 등
    aggregate_type VARCHAR(100),      -- RawData, Slice, View
    aggregate_id VARCHAR(255),        -- tenantId__entityKey
    payload JSONB NOT NULL,           -- 이벤트 Payload
    status VARCHAR(50) DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    trace_id VARCHAR(255),            -- OpenTelemetry traceId
    span_id VARCHAR(255),             -- OpenTelemetry spanId
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_outbox_job_id (job_id)  -- jobId로 조회
);
```

**분리하지 않는 이유**:
- Outbox Worker가 단일 폴링 루프로 모든 이벤트 처리
- jobId 기반 end-to-end 추적 용이
- 테이블 분리 시 조인 필요 (성능 저하)

---

### 3.3 jobId 추적 흐름

```
외부 서비스 jobId: "product-sync-20260212-001"
  ↓
SDK 호출: POST /api/v1/ingest { jobId: "product-sync-20260212-001", ... }
  ↓
Outbox 등록 (ViewsComposed):
  - job_id = "product-sync-20260212-001"
  - trace_id = "abc123..." (OpenTelemetry)
  ↓
ShipEventHandler: ViewsComposed 처리
  - SinkRule 조회
  - ShipRequested Outbox 생성 (job_id 전파)
  ↓
ShipWorkflow: Ship 실행
  - Sink 전송 (traceId 전파)
  ↓
모니터링: jobId로 전체 흐름 추적
  - Grafana: SELECT * FROM outbox WHERE job_id = 'product-sync-20260212-001'
  - Jaeger: traceId로 분산 트레이싱
```

---

## 4. SDK 설계

### 4.1 Kotlin SDK

```kotlin
// SDK 인터페이스
interface IvmLiteClient {
    suspend fun ingest(request: IngestRequest): IngestResponse
    suspend fun queryView(request: QueryViewRequest): QueryViewResponse
}

// 요청 모델
data class IngestRequest(
    val jobId: String,              // 외부 서비스 jobId
    val tenantId: String,
    val entityKey: String,
    val data: JsonObject,
    val ruleSetRef: String,         // "ruleset.core.v1@1.0.0"
    val viewDefId: String,          // "view.product.pdp.v1"
    val timeout: Duration = 5.seconds
)

// 응답 모델
data class IngestResponse(
    val jobId: String,
    val version: Long,
    val sliceCount: Int,
    val viewCount: Int,
    val sinkPending: Boolean,       // Sink 전송은 비동기 처리 중
    val traceId: String,            // OpenTelemetry traceId
    val durationMs: Long
)

// 구현체 (REST API 래퍼)
class IvmLiteClientImpl(
    private val baseUrl: String,
    private val httpClient: HttpClient
) : IvmLiteClient {

    override suspend fun ingest(request: IngestRequest): IngestResponse {
        return httpClient.post("$baseUrl/api/v1/ingest") {
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout { requestTimeoutMillis = request.timeout.inWholeMilliseconds }
        }.body()
    }
}
```

**사용 예시**:
```kotlin
val client = IvmLiteClientImpl(
    baseUrl = "http://localhost:8080",
    httpClient = HttpClient(CIO)
)

val response = client.ingest(
    IngestRequest(
        jobId = "product-sync-${UUID.randomUUID()}",
        tenantId = "oliveyoung",
        entityKey = "product:123456",
        data = buildJsonObject { put("name", "샴푸") },
        ruleSetRef = "ruleset.core.v1@1.0.0",
        viewDefId = "view.product.pdp.v1"
    )
)

println("✅ Slicing 완료: version=${response.version}, slices=${response.sliceCount}")
println("🚀 Sink 전송 비동기 처리 중 (jobId=${response.jobId})")
```

---

### 4.2 TypeScript SDK (신규)

```typescript
// SDK 인터페이스
export interface IvmLiteClient {
  ingest(request: IngestRequest): Promise<IngestResponse>;
  queryView(request: QueryViewRequest): Promise<QueryViewResponse>;
}

// 요청 모델
export interface IngestRequest {
  jobId: string;
  tenantId: string;
  entityKey: string;
  data: Record<string, any>;
  ruleSetRef: string;
  viewDefId: string;
  timeout?: number;  // milliseconds
}

// 응답 모델
export interface IngestResponse {
  jobId: string;
  version: number;
  sliceCount: number;
  viewCount: number;
  sinkPending: boolean;
  traceId: string;
  durationMs: number;
}

// 구현체
export class IvmLiteClientImpl implements IvmLiteClient {
  constructor(
    private baseUrl: string,
    private fetch: typeof globalThis.fetch = globalThis.fetch
  ) {}

  async ingest(request: IngestRequest): Promise<IngestResponse> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), request.timeout ?? 5000);

    try {
      const response = await this.fetch(`${this.baseUrl}/api/v1/ingest`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
        signal: controller.signal,
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } finally {
      clearTimeout(timeout);
    }
  }
}
```

**사용 예시**:
```typescript
const client = new IvmLiteClientImpl('http://localhost:8080');

const response = await client.ingest({
  jobId: `product-sync-${Date.now()}`,
  tenantId: 'oliveyoung',
  entityKey: 'product:123456',
  data: { name: '샴푸', price: 15000 },
  ruleSetRef: 'ruleset.core.v1@1.0.0',
  viewDefId: 'view.product.pdp.v1',
});

console.log(`✅ Slicing 완료: version=${response.version}, slices=${response.sliceCount}`);
console.log(`🚀 Sink 전송 비동기 처리 중 (jobId=${response.jobId})`);
```

---

## 5. API 설계

### 5.1 POST /api/v1/ingest

**Request**:
```json
{
  "jobId": "product-sync-20260212-001",
  "tenantId": "oliveyoung",
  "entityKey": "product:123456",
  "data": { "name": "샴푸", "price": 15000 },
  "ruleSetRef": "ruleset.core.v1@1.0.0",
  "viewDefId": "view.product.pdp.v1"
}
```

**Response** (200 OK):
```json
{
  "jobId": "product-sync-20260212-001",
  "version": 42,
  "sliceCount": 3,
  "viewCount": 2,
  "sinkPending": true,
  "traceId": "abc123def456...",
  "durationMs": 234
}
```

**Response** (400 Bad Request):
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid ruleSetRef format",
  "jobId": "product-sync-20260212-001"
}
```

**Response** (500 Internal Server Error):
```json
{
  "error": "SLICING_ERROR",
  "message": "Failed to execute RuleSet",
  "jobId": "product-sync-20260212-001",
  "traceId": "abc123def456..."
}
```

---

### 5.2 GET /api/v1/jobs/:jobId/status

**jobId로 처리 상태 조회**:

```http
GET /api/v1/jobs/product-sync-20260212-001/status
```

**Response** (200 OK):
```json
{
  "jobId": "product-sync-20260212-001",
  "version": 42,
  "slicingStatus": "COMPLETED",
  "shipStatus": "IN_PROGRESS",
  "sinks": [
    {
      "sinkType": "OPENSEARCH",
      "status": "COMPLETED",
      "completedAt": "2026-02-12T10:30:15Z"
    },
    {
      "sinkType": "PERSONALIZE",
      "status": "PENDING",
      "retryCount": 1
    }
  ],
  "events": [
    {
      "eventType": "ViewsComposed",
      "status": "COMPLETED",
      "processedAt": "2026-02-12T10:30:10Z"
    },
    {
      "eventType": "ShipRequested",
      "status": "PROCESSING",
      "createdAt": "2026-02-12T10:30:12Z"
    }
  ],
  "traceId": "abc123def456..."
}
```

**구현**:
```kotlin
class JobStatusQueryService(
    private val outboxRepo: OutboxRepositoryPort
) {
    suspend fun getJobStatus(jobId: String): JobStatusResponse {
        val events = outboxRepo.findByJobId(jobId)

        return JobStatusResponse(
            jobId = jobId,
            slicingStatus = if (events.any { it.eventType == "ViewsComposed" && it.status == "COMPLETED" })
                "COMPLETED" else "IN_PROGRESS",
            shipStatus = deriveShipStatus(events),
            sinks = deriveSinkStatuses(events),
            events = events.map { it.toEventStatus() }
        )
    }
}
```

---

## 6. 구현 계획

### Phase 1: SDK & API (2주)
- [ ] Kotlin SDK 구현 (`pkg/sdk/client/`)
- [ ] TypeScript SDK 구현 (신규 `sdk/typescript/` 모듈)
- [ ] POST /api/v1/ingest API 구현
- [ ] GET /api/v1/jobs/:jobId/status API 구현
- [ ] OpenAPI 3.1 스펙 생성
- [ ] SDK 단위 테스트 (Kotest, Vitest)

### Phase 2: Outbox jobId 통합 (1주)
- [ ] `outbox` 테이블에 `job_id` 컬럼 추가 (Flyway)
- [ ] `OutboxEntry.create()` jobId 파라미터 추가
- [ ] `IngestionOrchestrator.ingest()` jobId 전파
- [ ] `ShipEventHandler` jobId 전파 검증
- [ ] E2E 테스트: jobId end-to-end 추적

### Phase 3: 모니터링 & DX (1주)
- [ ] Grafana 대시보드: jobId 필터링
- [ ] Local 개발 가이드 (`docs/guides/local-development.md`)
- [ ] SDK 예제 코드 (`examples/kotlin/`, `examples/typescript/`)
- [ ] SDK 배포 자동화 (GitHub Releases)
- [ ] TypeScript SDK npm 배포

---

## 7. 모니터링

### 7.1 Grafana 쿼리

**jobId로 전체 흐름 조회**:
```sql
SELECT
    event_type,
    status,
    created_at,
    processed_at,
    retry_count,
    trace_id
FROM outbox
WHERE job_id = 'product-sync-20260212-001'
ORDER BY created_at;
```

**jobId별 Ship 상태**:
```sql
SELECT
    json_extract(payload, '$.sink') AS sink_type,
    status,
    retry_count,
    processed_at
FROM outbox
WHERE job_id = 'product-sync-20260212-001'
  AND event_type = 'ShipRequested';
```

### 7.2 OpenTelemetry 분산 트레이싱

```kotlin
// IngestionOrchestrator에서 traceId 생성
val span = tracer.spanBuilder("ingest").startSpan()
val traceId = span.spanContext.traceId

// Outbox에 traceId 저장
OutboxEntry.create(
    eventType = "ViewsComposed",
    payload = payload,
    traceId = traceId,
    jobId = command.jobId
)

// ShipWorkflow에서 traceId 전파
val parentSpan = Span.fromContext(Context.current())
val shipSpan = tracer.spanBuilder("ship")
    .setParent(parentSpan.spanContext)
    .startSpan()
```

**Jaeger 조회**:
- traceId로 전체 흐름 시각화
- Ingest → Slicing → ViewsComposed → ShipRequested → Ship → Sink

---

## 8. DX (Developer Experience)

### 8.1 TypeScript SDK 제공 (신규)
- npm 패키지: `@oliveyoung/ivm-lite-client`
- 타입 안전성: 100% TypeScript
- Tree-shakable ESM 번들
- 브라우저 + Node.js 지원

### 8.2 OpenAPI 3.1 스펙
- Swagger UI: http://localhost:8080/swagger
- 자동 생성: Ktor OpenAPI 플러그인
- SDK 코드 생성: `openapi-generator-cli`

### 8.3 Local 개발 가이드
```markdown
# Local Development

## 1. 환경 설정
source .env
docker-compose up -d postgres

## 2. IVM-Lite 서버 실행
just runtime-dev

## 3. SDK 테스트
cd examples/kotlin
./gradlew run

## 4. 모니터링
- Grafana: http://localhost:3001
- Jaeger: http://localhost:16686
```

### 8.4 SDK 예제 코드

**Kotlin**:
```kotlin
// examples/kotlin/src/main/kotlin/ProductSync.kt
fun main() = runBlocking {
    val client = IvmLiteClientImpl("http://localhost:8080")

    val response = client.ingest(
        IngestRequest(
            jobId = "product-sync-${UUID.randomUUID()}",
            tenantId = "oliveyoung",
            entityKey = "product:123456",
            data = buildJsonObject { put("name", "샴푸") },
            ruleSetRef = "ruleset.core.v1@1.0.0",
            viewDefId = "view.product.pdp.v1"
        )
    )

    println("✅ Slicing 완료: ${response.sliceCount} slices")
    println("🚀 Sink 전송 비동기 처리 중 (jobId=${response.jobId})")
}
```

**TypeScript**:
```typescript
// examples/typescript/src/product-sync.ts
import { IvmLiteClientImpl } from '@oliveyoung/ivm-lite-client';

const client = new IvmLiteClientImpl('http://localhost:8080');

const response = await client.ingest({
  jobId: `product-sync-${Date.now()}`,
  tenantId: 'oliveyoung',
  entityKey: 'product:123456',
  data: { name: '샴푸', price: 15000 },
  ruleSetRef: 'ruleset.core.v1@1.0.0',
  viewDefId: 'view.product.pdp.v1',
});

console.log(`✅ Slicing 완료: ${response.sliceCount} slices`);
console.log(`🚀 Sink 전송 비동기 처리 중 (jobId=${response.jobId})`);
```

---

## 9. 테스트 전략

### 9.1 SDK 단위 테스트

**Kotlin (Kotest)**:
```kotlin
class IvmLiteClientTest : DescribeSpec({
    describe("IvmLiteClient") {
        it("✅ ingest() 성공 시 IngestResponse 반환") {
            val mockEngine = MockEngine { request ->
                respond(
                    content = """{"jobId":"test","version":1,"sliceCount":2}""",
                    status = HttpStatusCode.OK
                )
            }
            val client = IvmLiteClientImpl("http://test", HttpClient(mockEngine))

            val response = client.ingest(IngestRequest(...))

            response.jobId shouldBe "test"
            response.sliceCount shouldBe 2
        }
    }
})
```

**TypeScript (Vitest)**:
```typescript
describe('IvmLiteClient', () => {
  it('✅ ingest() 성공 시 IngestResponse 반환', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ jobId: 'test', version: 1, sliceCount: 2 }),
    });
    const client = new IvmLiteClientImpl('http://test', mockFetch);

    const response = await client.ingest({...});

    expect(response.jobId).toBe('test');
    expect(response.sliceCount).toBe(2);
  });
});
```

### 9.2 E2E 테스트

```kotlin
it("✅ E2E: SDK → Ingest → Outbox (jobId 전파)") {
    // Given: SDK Client
    val client = IvmLiteClientImpl("http://localhost:8080")
    val jobId = "test-job-${UUID.randomUUID()}"

    // When: Ingest
    val response = client.ingest(
        IngestRequest(
            jobId = jobId,
            tenantId = "test",
            entityKey = "product:001",
            data = buildJsonObject { put("name", "Test") },
            ruleSetRef = "ruleset.core.v1@1.0.0",
            viewDefId = "view.product.pdp.v1"
        )
    )

    // Then: Response OK
    response.jobId shouldBe jobId
    response.sinkPending shouldBe true

    // Then: Outbox에 jobId 저장
    val events = outboxRepo.findByJobId(jobId).bind()
    events shouldHaveSize 1
    events[0].eventType shouldBe "ViewsComposed"
    events[0].jobId shouldBe jobId
}
```

---

## 10. 보안 고려사항

### 10.1 인증/인가
- API Key 기반 인증 (Header: `X-API-Key`)
- Tenant 격리 (tenantId 검증)
- Rate Limiting (jobId당 10 req/sec)

### 10.2 데이터 검증
- JSON Schema 기반 payload 검증
- entityKey 형식 검증 (`{entityType}:{id}`)
- ruleSetRef 버전 검증 (SemVer)

---

## 11. 장단점

### 장점
✅ **동기 보장**: RawData → Slicing까지 응답 타임아웃 내 완료
✅ **비동기 Ship**: Sink 전송 실패 시 재처리 자동화
✅ **공용 Outbox**: 단일 테이블로 jobId end-to-end 추적
✅ **SinkRule 라우팅**: 엔티티별 Sink 자동 결정 (중복 제거)
✅ **TypeScript SDK**: Frontend 통합 용이
✅ **OpenTelemetry**: 분산 트레이싱으로 디버깅 간소화

### 단점
⚠️ **Outbox 크기 증가**: 모든 이벤트가 단일 테이블에 저장 (파티셔닝 필요)
⚠️ **동기 타임아웃**: Slicing이 5초 초과 시 실패 (대용량 데이터 처리 제약)
⚠️ **SDK 유지보수**: Kotlin + TypeScript 2개 SDK 버전 관리

---

## 12. 결론

SDK 기반 외부 서비스 통합은 다음 이점을 제공합니다:

1. **개발자 경험 개선**: Kotlin/TypeScript SDK로 쉬운 통합
2. **동기/비동기 분리**: Slicing은 동기, Ship은 비동기
3. **end-to-end 추적**: jobId + traceId로 전체 흐름 모니터링
4. **확장성**: SinkRule 기반 자동 라우팅으로 Sink 추가 용이

**다음 단계**: Phase 1 (SDK & API 구현) 착수 승인 요청

---

## 13. 참고

- CRITICAL-ISSUES.md: Phase 1 완료 (ViewsComposed 핸들러 구현)
- RFC-IMPL-018: Hybrid Architecture Review
- RFC-017: Sink Plugin Architecture
