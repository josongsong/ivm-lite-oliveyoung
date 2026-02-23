# RFC-021: 최상급 DSL DX — Developer Experience

## 1. 비전

**"한 줄에 의도가 드러나는, 읽으면 곧 이해되는 API"**

- Ktor, Exposed, Kotlin Serialization 수준의 Kotlin DSL
- IDE 자동완성으로 90% 타이핑 없이 작성
- 컴파일 타임에 오류 검출
- 에러 메시지가 "어떻게 고칠지"를 알려줌

---

## 2. 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **문장처럼 읽힌다** | `product("SKU-001") named "비타민C" priced 15_000` |
| **프로퍼티 = 할당** | `tenantId = "x"` (함수 호출 아님) |
| **타입이 보호한다** | `Views.Product.Pdp` → `ProductPdpData` 반환 |
| **실패는 명시적** | `getOrThrow()` / `onFailure { }` |
| **짧은 경로** | `Ivm.query()` not `Ivm.client().query()` |

---

## 3. 제안 API (최상급)

### 3-1. Deploy DSL

```kotlin
// ═══════════════════════════════════════════════════════════════
// 현재
// ═══════════════════════════════════════════════════════════════
Ivm.product {
    tenantId("oliveyoung")  // 함수 호출
    sku("SKU-001")
    name("비타민C")
    price(15000)
}.deploy()

// ═══════════════════════════════════════════════════════════════
// 최상급: 프로퍼티 할당 (문서 예시와 일치)
// ═══════════════════════════════════════════════════════════════
Ivm.product {
    tenantId = "oliveyoung"
    sku = "SKU-001"
    name = "비타민C"
    price = 15_000
}.deploy()

// ═══════════════════════════════════════════════════════════════
// 최상급: 문장형 (도메인 리터럴)
// ═══════════════════════════════════════════════════════════════
Ivm.product("SKU-001") {
    named("비타민C")
    priced(15_000)
    by("라운드랩")
    inCategory("스킨케어")
}.deploy()

// ═══════════════════════════════════════════════════════════════
// 최상급: Result 처리
// ═══════════════════════════════════════════════════════════════
Ivm.product { sku = "A"; name = "A"; price = 1000 }
    .deploy()
    .onSuccess { println("v${it.version}") }
    .onFailure { log.error(it) }

// 또는
val result = Ivm.product { ... }.deploy()
result.getOrThrow()  // 실패 시 명시적 예외
result.getOrNull()   // 실패 시 null
```

### 3-2. Query DSL

```kotlin
// ═══════════════════════════════════════════════════════════════
// 현재
// ═══════════════════════════════════════════════════════════════
Ivm.client().query("product.pdp").key("SKU-001").get()

// ═══════════════════════════════════════════════════════════════
// 최상급: Ivm 직접 (client 생략)
// ═══════════════════════════════════════════════════════════════
Ivm.query("product.pdp").key("SKU-001").get()

// ═══════════════════════════════════════════════════════════════
// 최상급: 타입 세이프 + getOrThrow
// ═══════════════════════════════════════════════════════════════
val pdp: ProductPdpData = Ivm.query(Views.Product.Pdp)
    .key("SKU-001")
    .getOrThrow()

// ═══════════════════════════════════════════════════════════════
// 최상급: ViewRef 확장 (가장 짧음)
// ═══════════════════════════════════════════════════════════════
val pdp = Views.Product.Pdp["SKU-001"].getOrThrow()

// ═══════════════════════════════════════════════════════════════
// 최상급: 범위 검색 (문장형)
// ═══════════════════════════════════════════════════════════════
Ivm.query(Views.Product.Search)
    .tenant("oliveyoung")
    .where { keyPrefix("SKU-") }
    .limit(100)
    .list()
```

### 3-3. Batch DSL

```kotlin
// ═══════════════════════════════════════════════════════════════
// 최상급: 배치 Deploy
// ═══════════════════════════════════════════════════════════════
Ivm.batch {
    product { sku = "A"; name = "A"; price = 1000 }
    product { sku = "B"; name = "B"; price = 2000 }
    brand { code = "RL"; name = "라운드랩" }
}.deployAll()

// 결과
// BatchResult(successCount = 3, failures = [], ...)
```

### 3-4. Pipeline DSL (선택)

```kotlin
// ═══════════════════════════════════════════════════════════════
// 최상급: 파이프라인 선언 (고급)
// ═══════════════════════════════════════════════════════════════
Ivm.pipeline {
    ingest(product { sku = "A"; ... })
    slice(ruleset = "ruleset.product.doc001.v1")
    ship(to = "opensearch")
}.run()
```

---

## 4. 구현 로드맵

### Phase 1: 즉시 적용 (1~2일)

| 항목 | 변경 | 파일 |
|------|------|------|
| **프로퍼티 할당** | `var tenantId` + setter | ProductDsl, BrandDsl, CategoryDsl |
| **Ivm.query()** | `fun query(viewId: String)` | Ivm.kt |
| **getOrThrow** | `DeployableContext`, `ViewResult` | DeployableContext, QueryApi |
| **문서 예시 수정** | 실제 동작과 일치 | Ivm.kt KDoc |

### Phase 2: 문장형 (3~5일)

| 항목 | 변경 |
|------|------|
| **product(sku)** | `fun product(sku: String, block: ProductBuilder.() -> Unit)` |
| **named(), priced()** | `fun named(s: String)`, `fun priced(n: Long)` |
| **by(), inCategory()** | `fun by(brand: String)`, `fun inCategory(cat: String)` |

### Phase 3: Batch (1주)

| 항목 | 변경 |
|------|------|
| **Ivm.batch { }** | `BatchContext` + `deployAll()` |
| **BatchResult** | `successCount`, `failures`, `summary()` |

### Phase 4: Pipeline (선택, 2주)

| 항목 | 변경 |
|------|------|
| **Ivm.pipeline { }** | `PipelineContext` |
| **ingest(), slice(), ship()** | 단계별 실행 |

---

## 5. 에러 메시지 DX

```kotlin
// 현재
"tenantId is required"

// 최상급
"ProductBuilder: tenantId is required. Add: tenantId = \"oliveyoung\""
"Deploy failed for SKU-001: Slice ENRICHED depends on CORE (run CORE first)"
"View product.pdp not found. Available: product.core, product.search"
```

---

## 6. 코드 예시 (Before/After)

### Before

```kotlin
Ivm.product {
    tenantId("oliveyoung")
    sku("SKU-001")
    name("비타민C")
    price(15000)
}.deploy()

val view = Ivm.client().query().view("product.pdp").key("SKU-001").get()
if (view.success) {
    val data = view.data
    // ...
}
```

### After (최상급)

```kotlin
Ivm.product {
    tenantId = "oliveyoung"
    sku = "SKU-001"
    name = "비타민C"
    price = 15_000
}.deploy().onSuccess { println("Deployed v${it.version}") }

val pdp: ProductPdpData = Ivm.query(Views.Product.Pdp)
    .key("SKU-001")
    .getOrThrow()
println("${pdp.name}: ${pdp.price}원")
```

---

## 7. 참고: Kotlin DSL 베스트 프랙티스

- **Ktor**: `routing { get("/") { call.respondText("OK") } }` — 람다 스코프
- **Exposed**: `Users.select { Users.id eq 1 }` — 타입 세이프 쿼리
- **Kotlin Serialization**: `buildJsonObject { "key" to value }` — 빌더
- **Arrow**: `either { ... }.onLeft { }` — 함수형 에러 처리

---

## 8. 결론

| Phase | 효과 | 공수 |
|-------|------|------|
| Phase 1 | 문서-코드 일치, query 단축, getOrThrow | 1~2일 |
| Phase 2 | 문장형 표현력 | 3~5일 |
| Phase 3 | 배치 DX | 1주 |
| Phase 4 | 파이프라인 (선택) | 2주 |

**권장**: Phase 1 즉시 적용 → Phase 2 검토 → Phase 3 필요 시
