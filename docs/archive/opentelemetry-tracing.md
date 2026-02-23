# OpenTelemetry Tracing — 연동 현황

> **RFC-IMPL-009**: Tracing SSOT (Single Source Of Truth)  
> **상태**: ✅ Production Ready  
> **최종 업데이트**: 2026-01-27

---

## ✅ 연동 상태

**OpenTelemetry가 완전히 연동되어 있습니다!**

- ✅ OTLP Exporter 설정 완료
- ✅ 모든 Workflow에 span 생성
- ✅ 모든 Repository에 span 생성
- ✅ HTTP 요청 span 생성
- ✅ MDC 연동 (Log Correlation)
- ✅ Koin DI 연동

---

## 📊 설정 현황

### 1. 의존성 (build.gradle.kts)

```kotlin
// OpenTelemetry (Tracing SSOT)
implementation("io.opentelemetry:opentelemetry-api:1.36.0")
implementation("io.opentelemetry:opentelemetry-sdk:1.36.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.36.0")

// Ktor OTel instrumentation (하이브리드용)
implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:2.23.0-alpha")
```

---

### 2. 설정 파일 (application.yaml)

```yaml
observability:
  metricsEnabled: true
  tracingEnabled: true
  otlpEndpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}
```

**환경 변수**:
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OTLP Collector 엔드포인트 (기본값: `http://localhost:4317`)

---

### 3. TracingConfig 초기화

**파일**: `shared/config/TracingConfig.kt`

```kotlin
object TracingConfig {
    fun init(config: ObservabilityConfig): OpenTelemetry {
        if (!config.tracingEnabled) {
            return OpenTelemetry.noop()
        }

        val exporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.otlpEndpoint)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(
                Resource.builder()
                    .put("service.name", "ivm-lite")
                    .put("service.version", System.getProperty("service.version") ?: "unknown")
                    .put("deployment.environment", System.getenv("ENVIRONMENT") ?: "development")
                    .build(),
            )
            .build()

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal()
    }
}
```

**특징**:
- ✅ OTLP gRPC Exporter 사용
- ✅ BatchSpanProcessor로 성능 최적화
- ✅ Resource attributes 설정 (service.name, version, environment)
- ✅ GlobalOpenTelemetry에 등록

---

### 4. Koin DI 연동

**파일**: `apps/runtimeapi/wiring/TracingModule.kt`

```kotlin
val tracingModule = module {
    single<OpenTelemetry> {
        TracingConfig.init(get<AppConfig>().observability)
    }
    single<Tracer> {
        get<OpenTelemetry>().getTracer("ivm-lite")
    }
}
```

**사용**:
- 모든 Workflow와 Repository에서 `get<Tracer>()`로 주입받아 사용

---

## 🔧 Span 생성 헬퍼 함수

**파일**: `shared/adapters/TracingExtensions.kt`

### withSpanSuspend (Suspend 함수용)

```kotlin
suspend inline fun <T> Tracer.withSpanSuspend(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend (Span) -> T,
): T {
    val span = spanBuilder(name)
        .apply {
            attributes.forEach { (k, v) -> setAttribute(k, v) }
        }
        .startSpan()

    val scope: Scope = span.makeCurrent()
    return try {
        val result = block(span)
        span.setStatus(StatusCode.OK)
        result
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, e.message ?: "unknown")
        span.recordException(e)
        throw e
    } finally {
        scope.close()
        span.end()
    }
}
```

**특징**:
- ✅ Coroutine context 전파 (suspend 함수 지원)
- ✅ 자동 에러 처리 및 기록
- ✅ StatusCode 자동 설정
- ✅ Scope 자동 관리

---

## 📍 Span 사용 현황

### 1. Workflow 레벨

| Workflow | Span Name | Attributes |
|----------|-----------|------------|
| **IngestWorkflow** | `IngestWorkflow.execute` | `tenant_id`, `entity_key`, `version`, `schema_id`, `schema_version`, `transactional` |
| **SlicingWorkflow** | `SlicingWorkflow.execute` | `tenant_id`, `entity_key`, `version`, `mode`, `ruleset_ref` |
| **SlicingWorkflow** | `SlicingWorkflow.executeAuto` | `tenant_id`, `entity_key`, `version`, `mode` |
| **SlicingWorkflow** | `SlicingWorkflow.executeIncremental` | `tenant_id`, `entity_key`, `from_version`, `to_version` |
| **QueryViewWorkflow** | `QueryViewWorkflow.query` | `tenant_id`, `view_id`, `entity_key` |
| **FanoutWorkflow** | `FanoutWorkflow.execute` | `tenant_id`, `ref_entity_key`, `ref_version`, `index_type`, `index_value` |
| **OutboxPollingWorker** | `OutboxWorker.processEntry` | `entry_id`, `aggregate_type`, `event_type` |

**예시** (`IngestWorkflow.kt:66`):

```kotlin
return tracer.withSpanSuspend(
    "IngestWorkflow.execute",
    mapOf(
        "tenant_id" to tenantId.value,
        "entity_key" to entityKey.value,
        "version" to version.toString(),
        "schema_id" to schemaId,
        "schema_version" to schemaVersion.toString(),
        "transactional" to (unitOfWork != null).toString(),
    ),
) {
    // ... 실제 로직
}
```

---

### 2. Repository 레벨

| Repository | Span Name | Attributes |
|-----------|-----------|------------|
| **JooqRawDataRepository** | `PostgreSQL.putIdempotent` | `db.system`, `db.operation`, `tenant_id`, `entity_key`, `version` |
| **JooqRawDataRepository** | `PostgreSQL.get` | `db.system`, `db.operation`, `tenant_id`, `entity_key`, `version` |
| **JooqSliceRepository** | `PostgreSQL.putAllIdempotent` | `db.system`, `db.operation`, `tenant_id`, `entity_key`, `version` |
| **JooqSliceRepository** | `PostgreSQL.getByVersion` | `db.system`, `db.operation`, `tenant_id`, `entity_key`, `version` |
| **JooqOutboxRepository** | `PostgreSQL.claim` | `db.system`, `db.operation`, `batch_size`, `aggregate_type` |
| **JooqOutboxRepository** | `PostgreSQL.insert` | `db.system`, `db.operation`, `aggregate_type`, `event_type` |
| **JooqOutboxRepository** | `PostgreSQL.markProcessed` | `db.system`, `db.operation`, `count` |
| **JooqIngestUnitOfWork** | `PostgreSQL.executeIngest` | `db.system`, `db.operation`, `tenant_id`, `entity_key`, `version` |
| **DynamoDBContractRegistryAdapter** | `DynamoDB.getRuleSet` | `db.system`, `db.operation`, `ruleset_id`, `ruleset_version` |
| **DynamoDBContractRegistryAdapter** | `DynamoDB.getViewDefinition` | `db.system`, `db.operation`, `view_id`, `view_version` |

**예시** (`JooqRawDataRepository.kt:61`):

```kotlin
override suspend fun putIdempotent(record: RawDataRecord): RawDataRepositoryPort.Result<Unit> =
    tracer.withSpanSuspend(
        "PostgreSQL.putIdempotent",
        mapOf(
            "db.system" to "postgresql",
            "db.operation" to "insert",
            "tenant_id" to record.tenantId.value,
            "entity_key" to record.entityKey.value,
            "version" to record.version.toString(),
        ),
    ) {
        // ... 실제 DB 작업
    }
```

---

### 3. HTTP 요청 레벨

**파일**: `apps/runtimeapi/Application.kt:64`

```kotlin
install(createApplicationPlugin("HttpTracing") {
    onCall { call ->
        val method = call.request.local.method.value
        val path = call.request.local.uri
        val span = tracer.spanBuilder("HTTP $method $path")
            .setAttribute("http.method", method)
            .setAttribute("http.target", path)
            .setAttribute("http.route", path)
            .startSpan()
        
        val scope = span.makeCurrent()
        call.attributes.put(otelScopeKey, scope)
        call.attributes.put(otelSpanKey, span)
        
        if (span.spanContext.isValid) {
            MDC.put("traceId", span.spanContext.traceId)
            MDC.put("spanId", span.spanContext.spanId)
        }
    }
    onCallRespond { call, _ ->
        val span = call.attributes.getOrNull(otelSpanKey)
        span?.let {
            it.setAttribute("http.status_code", call.response.status()?.value?.toLong() ?: 0L)
            it.setStatus(
                if ((call.response.status()?.value ?: 0) < 400) {
                    StatusCode.OK
                } else {
                    StatusCode.ERROR
                }
            )
            it.end()
        }
    }
})
```

**특징**:
- ✅ HTTP 메서드, 경로, 상태 코드 기록
- ✅ MDC 연동 (Log Correlation)
- ✅ Span context 전파

---

## 🔗 Span 계층 구조

```
HTTP POST /api/v1/products
  └─ IngestWorkflow.execute
      └─ PostgreSQL.executeIngest (트랜잭션)
          ├─ PostgreSQL.putIdempotent (RawData)
          └─ PostgreSQL.insert (Outbox)
  └─ OutboxWorker.processEntry
      └─ SlicingWorkflow.executeAuto
          ├─ PostgreSQL.get (RawData 조회)
          ├─ DynamoDB.getRuleSet (RuleSet 조회)
          ├─ DynamoDB.putAllIdempotent (Slice 저장)
          └─ DynamoDB.putAllIdempotent (InvertedIndex 저장)
      └─ ShipEventHandler.handleSliceEvent
          └─ ShipWorkflow.execute
              └─ DynamoDB.getByVersion (Slice 조회)
              └─ OpenSearch.ship (Sink 전달)
```

---

## 📊 Span Attributes 표준

### Database Attributes

| Attribute | 값 | 설명 |
|-----------|-----|------|
| `db.system` | `postgresql`, `dynamodb` | 데이터베이스 시스템 |
| `db.operation` | `insert`, `select`, `update` | 작업 타입 |

### HTTP Attributes

| Attribute | 값 | 설명 |
|-----------|-----|------|
| `http.method` | `GET`, `POST`, `PUT`, `DELETE` | HTTP 메서드 |
| `http.target` | `/api/v1/products` | 요청 경로 |
| `http.route` | `/api/v1/products` | 라우트 패턴 |
| `http.status_code` | `200`, `404`, `500` | HTTP 상태 코드 |

### Business Attributes

| Attribute | 값 | 설명 |
|-----------|-----|------|
| `tenant_id` | `oliveyoung` | 테넌트 ID |
| `entity_key` | `PRODUCT:SKU-001` | 엔티티 키 |
| `version` | `1234567890` | 버전 |
| `aggregate_type` | `RAW_DATA`, `SLICE` | Aggregate 타입 |
| `event_type` | `RawDataIngested`, `ShipRequested` | 이벤트 타입 |

---

## 🧪 테스트 환경

**파일**: `test/TestTracer.kt`

```kotlin
object TestTracer {
    val instance: Tracer = OpenTelemetry.noop().getTracer("test")
}
```

**사용**:
- 테스트에서는 `noop()` Tracer 사용 (실제 span 생성 안 함)
- 성능 영향 없음

---

## 🚀 실행 방법

### 1. OTLP Collector 실행 (로컬)

```bash
# Docker Compose로 실행
docker-compose up -d otel-collector

# 또는 직접 실행
docker run -p 4317:4317 -p 4318:4318 \
  -v $(pwd)/otel-collector-config.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector:latest
```

### 2. 환경 변수 설정

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export ENVIRONMENT=development
```

### 3. 애플리케이션 실행

```bash
./gradlew run
```

---

## 📈 모니터링

### Jaeger (로컬)

```bash
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest
```

**접속**: http://localhost:16686

### Grafana Tempo (Production)

```yaml
# otel-collector-config.yaml
exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      exporters: [otlp/tempo]
```

---

## ✅ 검증 체크리스트

- [x] OTLP Exporter 설정 완료
- [x] 모든 Workflow에 span 생성
- [x] 모든 Repository에 span 생성
- [x] HTTP 요청 span 생성
- [x] MDC 연동 (Log Correlation)
- [x] Koin DI 연동
- [x] 에러 자동 기록
- [x] Span 계층 구조 유지
- [x] 표준 Attributes 사용

---

## 🔍 문제 해결

### Span이 생성되지 않는 경우

1. **설정 확인**:
   ```yaml
   observability:
     tracingEnabled: true
     otlpEndpoint: http://localhost:4317
   ```

2. **OTLP Collector 확인**:
   ```bash
   curl http://localhost:4317/health
   ```

3. **로그 확인**:
   ```bash
   # Span 생성 로그 확인
   grep "span" logs/application.log
   ```

### Span이 전송되지 않는 경우

1. **네트워크 확인**:
   ```bash
   telnet localhost 4317
   ```

2. **OTLP Collector 로그 확인**:
   ```bash
   docker logs otel-collector
   ```

---

## 📚 관련 문서

- [RFC-IMPL-009](../rfc_archive/rfcimpl009.md) - Observability SSOT
- [OpenTelemetry 공식 문서](https://opentelemetry.io/docs/)
- [OTLP Exporter 가이드](https://opentelemetry.io/docs/specs/otel/protocol/exporter/)

---

## 🎯 결론

**OpenTelemetry Tracing이 완전히 연동되어 있습니다!**

- ✅ 모든 주요 컴포넌트에 span 생성
- ✅ 표준 Attributes 사용
- ✅ 에러 자동 기록
- ✅ Log Correlation 지원
- ✅ Production Ready

**다음 단계**:
- [ ] Distributed Tracing 테스트 (여러 서비스 간)
- [ ] Span Sampling 전략 수립
- [ ] Custom Metrics 추가
