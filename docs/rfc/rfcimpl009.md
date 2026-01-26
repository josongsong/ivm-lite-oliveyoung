RFC-IMPL-009 — Infrastructure Stack Decisions & Guardrails (인프라 헌법)

Status: Accepted
Created: 2026-01-25
Scope: 런타임 인프라 스택 선정 + **강제 규약(Guardrails)**
Depends on: RFC-IMPL-001, RFC-IMPL-007, RFC-IMPL-008
Audience: Platform / Infra / All Contributors
Non-Goals: 비즈니스 로직, 도메인 설계

---

## 0. Executive Summary

본 RFC는 ivm-lite 런타임의 **인프라 스택**과 **강제 규약(Guardrails)**을 정의한다.

**스택 선택은 적절하며 엔진형 런타임(SOTA)에 부합함.**
다만, **SSOT 경계 / 결정성 / wiring 위치를 명확히 고정**해야 장기적으로 흔들리지 않음.

### 스택 요약

| 영역 | 선택 | Engine |
|------|------|--------|
| HTTP Server | **Ktor 2.x** | **Netty** (고정) |
| HTTP Client | **Ktor Client** | **CIO** (고정) |
| DI | **Koin 3.x** | - |
| Config | **Hoplite** | - |
| Serialization | **kotlinx.serialization** | - |
| Observability | **OpenTelemetry + Micrometer** | - |
| Resilience | **Resilience4j** | - |
| Testing | **Kotest + MockK + Testcontainers** | - |

### 본 RFC의 역할

> **"무슨 기술을 쓰는가"** 가 아니라,  
> **"어디까지 허용하고, 어디서부터 금지하는가"** 를 고정하는 **인프라 헌법**

---

## 1. Dependency Injection (DI) 규칙 — P0

### 1-1. Wiring 위치 고정 (필수)

| 허용 위치 | 설명 |
|-----------|------|
| `apps/*/wiring/` | 앱 엔트리포인트의 DI 모듈 |
| `package/orchestration/wiring/` | 오케스트레이션 전용 wiring |

### 1-2. 금지 규칙

| 금지 | 이유 |
|------|------|
| `shared/` 패키지에 DI 모듈 | shared는 "기반 라이브러리"이며 프레임워크 오염 차단 |
| `domain/` 패키지에 DI 모듈 | 도메인은 순수 로직만 |

### 1-3. 폴더 구조

```
apps/runtimeapi/
├── wiring/
│   ├── AppModule.kt           # Koin 메인 모듈
│   ├── AdapterModule.kt       # 어댑터 바인딩
│   └── InfraModule.kt         # DB, HTTP Client 등
├── Application.kt
└── routes/

package/orchestration/
├── wiring/
│   └── WorkflowModule.kt      # 워크플로우 바인딩
└── application/
    └── IngestWorkflow.kt
```

### 1-4. 이유

- **shared**는 "기반 라이브러리"이며 프레임워크 오염을 차단해야 함
- **실행 환경별 wiring**은 entry(apps) 또는 orchestration 소유

---

## 2. Serialization & Determinism — P0

### 2-1. 직렬화 역할 분리 (필수)

| 용도 | 기술 | 경로 |
|------|------|------|
| **API 요청/응답** | `kotlinx.serialization` | Ktor Content Negotiation |
| **해시/멱등/서명용** | **RFC8785 Canonical JSON** | `shared/domain/determinism/CanonicalJson.kt` |

### 2-2. 금지 규칙 (필수)

| 금지 | 이유 |
|------|------|
| API JSON 직렬화 결과를 그대로 해시/멱등 키로 사용 | 직렬화 순서/공백이 보장되지 않음 |
| Hash에 non-canonical JSON 사용 | 결정성 깨짐 |

### 2-3. 올바른 패턴

```kotlin
// ❌ 잘못된 패턴 - API JSON을 그대로 해시
val apiJson = Json.encodeToString(request)
val hash = sha256(apiJson)  // 금지!

// ✅ 올바른 패턴 - Canonical JSON 경유
val canonical = CanonicalJson.canonicalize(content)  // RFC8785
val hash = Hashing.sha256(canonical)
```

### 2-4. Hash 순서 고정 (필수)

```
Input → canonicalize (RFC8785) → hash (SHA256) → hex string
```

---

## 3. Observability SSOT 분리 — P0

### 3-1. 역할 고정 (필수)

| 영역 | SSOT | 라이브러리 |
|------|------|-----------|
| **Tracing** | OpenTelemetry | `opentelemetry-api`, `opentelemetry-sdk` |
| **Metrics** | Micrometer | `micrometer-core`, `micrometer-registry-prometheus` |
| **Log Correlation** | traceId/spanId | **MDC 필수** |

### 3-2. Export 경로 (필수)

| 영역 | Export 방식 |
|------|-------------|
| **Tracing** | OTLP (gRPC/HTTP) → Jaeger/Tempo |
| **Metrics** | Prometheus scrape (`/metrics`) |
| **Logs** | JSON 구조화 로그 → Loki/CloudWatch |

### 3-3. Log Correlation 필수 패턴

```kotlin
// MDC에 traceId/spanId 필수
MDC.put("traceId", Span.current().spanContext.traceId)
MDC.put("spanId", Span.current().spanContext.spanId)
```

### 3-4. Ktor Instrumentation 정책

| 허용 | 조건 |
|------|------|
| OTel Ktor instrumentation (alpha) 사용 가능 | 아래 조건 충족 시 |

**Alpha 의존성 사용 조건**:
- 업그레이드 추적 책임 명시 (CHANGELOG 확인)
- 장애 시 **수동 instrumentation으로 fallback 가능**해야 함
- 버전 pinning 필수

---

## 4. Resilience 적용 규칙 — P0

### 4-1. 적용 경계 (필수)

| 허용 | 금지 |
|------|------|
| `adapters/` (외부 시스템 경계) | `domain/`, `orchestration/` 내부 |

```kotlin
// ✅ 올바른 위치 - Adapter에서 적용
class DynamoDBContractRegistryAdapter : ContractRegistryPort {
    private val circuitBreaker = CircuitBreaker.of("dynamodb", ...)
    
    override suspend fun load(ref: ContractRef) = 
        circuitBreaker.executeSuspendFunction { ... }
}

// ❌ 잘못된 위치 - Orchestration에서 적용
class IngestWorkflow {
    private val retry = Retry.of(...)  // 금지!
}
```

### 4-2. Retry 규칙 (필수)

| 허용 | 금지 |
|------|------|
| **멱등(idempotent) 호출**에만 Retry | **non-idempotent 호출**에는 Retry 금지 |

```kotlin
// ✅ 멱등 호출 - Retry 허용
suspend fun loadContract(ref: ContractRef) = retry.execute { dynamoDb.getItem(...) }

// ❌ non-idempotent - Retry 금지
suspend fun createOrder() = retry.execute { ... }  // 중복 생성 위험!
```

---

## 5. Messaging / Outbox / Kafka — P0

### 5-1. 본 RFC의 범위

| 포함 | 불포함 |
|------|--------|
| Kafka / Debezium / Outbox를 **Optional 인프라**로 정의 | SSOT 시점, 상태 머신, 재시도 정책 |

### 5-2. SSOT 분리

| 항목 | RFC |
|------|-----|
| Outbox 스키마, Debezium 설정 | RFC-IMPL-008 |
| Schema Registry (DynamoDB) | RFC-IMPL-007 |
| 이벤트 재시도/보상 정책 | (향후) RFC-IMPL-011 계열 |

**본 RFC는 "런타임 스택"에만 책임을 둠.**

---

## 6. Ktor Engine 선택 — P1

### 6-1. Engine 고정

| 용도 | Engine | 이유 |
|------|--------|------|
| **Server** | **Netty** | 운영 안정성, 성능 |
| **Client** | **CIO** | Kotlin-native, 코루틴 최적화 |

### 6-2. 의존성

```kotlin
// Server: Netty 고정
implementation("io.ktor:ktor-server-netty:$ktorVersion")

// Client: CIO 고정
implementation("io.ktor:ktor-client-cio:$ktorVersion")
```

### 6-3. 이유

- 운영 안정성 + 디버깅 일관성
- CIO는 JVM 네이티브로 코루틴과 잘 맞음

---

## 7. Configuration 규칙 — P1

### 7-1. Hoplite 정책 (필수)

| 항목 | 규칙 |
|------|------|
| **우선순위** | `Env > Resource YAML > defaults` |
| **Unknown key** | **fail-closed** (알 수 없는 키는 에러) |
| **Secret 값** | **반드시 `Secret` 타입 사용** |

### 7-2. ConfigLoader 설정

```kotlin
val config = ConfigLoaderBuilder.default()
    .addEnvironmentSource()               // 1순위: 환경변수
    .addResourceSource("/application.yaml") // 2순위: YAML
    .strict()                              // unknown key → fail
    .build()
    .loadConfigOrThrow<AppConfig>()
```

### 7-3. Secret 타입 필수

```kotlin
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: Secret,  // ✅ Secret 타입 필수
    // val password: String,  // ❌ 금지
)
```

---

## 8. Testing 계층 규약 — P1

### 8-1. 테스트 유형별 규칙

| 계층 | 테스트 방식 | 의존성 |
|------|------------|--------|
| `domain/` | **Pure unit test** | No container, no mock |
| `orchestration/` | **Port mock 기반** | MockK |
| `adapters/` | **Testcontainers 통합 테스트** | PostgreSQL, DynamoDB Local |
| `apps/` | **Ktor Test Host** | End-to-end |

### 8-2. 예시

```kotlin
// domain/ - Pure unit test (no dependencies)
class CanonicalJsonTest : StringSpec({
    "should produce stable output" {
        val result = CanonicalJson.canonicalize(mapOf("b" to 1, "a" to 2))
        result shouldBe """{"a":2,"b":1}"""
    }
})

// orchestration/ - Port mock
class IngestWorkflowTest : StringSpec({
    "should store raw data" {
        val mockRepo = mockk<RawDataRepositoryPort>()
        coEvery { mockRepo.putIdempotent(any()) } returns Result.Ok(...)
        
        val workflow = IngestWorkflow(mockRepo)
        workflow.execute(request) shouldBe success
    }
})

// adapters/ - Testcontainers
class JooqRawDataRepositoryTest : StringSpec({
    val postgres = PostgreSQLContainer("postgres:16-alpine")
    
    "should persist and retrieve" {
        val repo = JooqRawDataRepository(createDSL(postgres))
        repo.putIdempotent(record)
        repo.get(tenantId, entityKey, version) shouldBe record
    }
})
```

---

## 9. Health / Readiness 규칙 — P2

### 9-1. 엔드포인트

| 엔드포인트 | 용도 | 체크 항목 |
|------------|------|----------|
| `GET /health` | Liveness | 앱 프로세스 실행 중 |
| `GET /ready` | Readiness | **현재 wiring된 외부 의존성** |
| `GET /metrics` | Prometheus | Micrometer 메트릭 |

### 9-2. Readiness 규칙 (필수)

| 올바른 방식 | 잘못된 방식 |
|-------------|-------------|
| **현재 wiring된 Port Adapter** 기준 체크 | 단순 DB ping |

```kotlin
// ✅ 올바른 방식 - 실제 wiring된 의존성 체크
fun Route.readinessRoutes(
    adapters: List<HealthCheckable>,  // 현재 DI로 주입된 어댑터들
) {
    get("/ready") {
        val checks = adapters.associate { it.name to it.healthCheck() }
        val allHealthy = checks.values.all { it }
        // ...
    }
}

// ❌ 잘못된 방식 - 하드코딩된 체크
get("/ready") {
    checkPostgres()  // DI와 무관하게 하드코딩
    checkDynamoDB()
}
```

---

## 10. 의존성 요약

```kotlin
// build.gradle.kts

val ktorVersion = "2.3.9"
val koinVersion = "3.5.3"
val hopliteVersion = "2.7.5"
val otelVersion = "1.36.0"
val micrometerVersion = "1.12.4"
val resilience4jVersion = "2.2.0"

dependencies {
    // === HTTP (Ktor) ===
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")  // Server: Netty 고정
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")   // Client: CIO 고정
    
    // === DI (Koin) ===
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    
    // === Config (Hoplite) ===
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
    
    // === Serialization ===
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // === Observability ===
    implementation("io.opentelemetry:opentelemetry-api:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-sdk:$otelVersion")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:$otelVersion")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // === Resilience ===
    implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    
    // === Testing ===
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.testcontainers:postgresql:1.19.7")
}
```

---

## 11. Guardrails Summary (강제 규약 요약)

### P0 (필수, PR 거부)

| # | 규칙 | 검증 |
|---|------|------|
| 1 | DI/Wiring은 `apps/*/wiring` 또는 `orchestration/wiring`에만 | ArchUnit |
| 2 | `shared/`에 DI 모듈 금지 | ArchUnit |
| 3 | API JSON을 해시/멱등 키로 직접 사용 금지 | Code Review |
| 4 | Hash는 반드시 `canonicalize → hash` 순서 | Unit Test |
| 5 | Tracing SSOT = OpenTelemetry | - |
| 6 | Metrics SSOT = Micrometer | - |
| 7 | Log Correlation = traceId/spanId (MDC 필수) | Code Review |
| 8 | Resilience4j는 `adapters/`에서만 적용 | ArchUnit |
| 9 | Retry는 멱등 호출에만 허용 | Code Review |
| 10 | Kafka/Outbox SSOT는 별도 RFC (008, 011) | - |

### P1 (권장, 위반 시 명시적 예외 필요)

| # | 규칙 |
|---|------|
| 1 | Server Engine = Netty |
| 2 | Client Engine = CIO |
| 3 | Config 우선순위: Env > YAML > defaults |
| 4 | Unknown config key = fail-closed |
| 5 | Secret 값은 `Secret` 타입 필수 |
| 6 | domain = pure unit test, adapters = Testcontainers |

### P2 (권장)

| # | 규칙 |
|---|------|
| 1 | Readiness = 현재 wiring된 Adapter 기준 체크 |

---

## 12. ArchUnit 규칙 추가 (RFC-IMPL-001 연동)

```kotlin
// ArchitectureConstraintsTest.kt에 추가

"RFC-IMPL-009: DI/Wiring은 허용된 위치에만" {
    noClasses()
        .that().resideInAPackage("..shared..")
        .should().dependOnClassesThat()
        .resideInAPackage("org.koin..")
        .`as`("shared 패키지에서 Koin(DI) 사용 금지")
        .check(classes)
}

"RFC-IMPL-009: Resilience4j는 adapters에서만" {
    noClasses()
        .that().resideInAPackage("..domain..")
        .or().resideInAPackage("..orchestration.application..")
        .should().dependOnClassesThat()
        .resideInAPackage("io.github.resilience4j..")
        .`as`("domain/orchestration에서 Resilience4j 사용 금지")
        .check(classes)
}
```

---

## 13. Acceptance Criteria

- [ ] DI 모듈이 `apps/*/wiring` 또는 `orchestration/wiring`에만 존재
- [ ] `shared/`에 Koin 의존성 없음
- [ ] 해시 계산 시 `CanonicalJson.canonicalize()` 경유 확인
- [ ] MDC에 traceId/spanId 설정 확인
- [ ] Resilience4j가 `adapters/`에서만 사용됨
- [ ] Hoplite `strict()` 모드 활성화
- [ ] ArchUnit 규칙 추가 및 통과

---

## 14. Final Judgment

| 항목 | 결정 |
|------|------|
| **Status** | **Accepted (with Guardrails)** |
| **역할** | 인프라 스택 + 강제 규약 (인프라 헌법) |

**한 줄 요약**: 무슨 기술을 쓰는가가 아니라, **어디까지 허용하고 어디서부터 금지하는가**를 고정.

---

## 15. Next Steps

1. **RFC-IMPL-010**: API 스펙 정의 (OpenAPI)
2. **RFC-IMPL-011**: 인증/인가 (JWT, RBAC) + 이벤트 재시도 정책
3. **RFC-IMPL-012**: 배포 (Kubernetes manifests, Helm)
