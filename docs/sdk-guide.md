# IVM SDK 사용 가이드

> **버전**: 2.0.0  
> **최종 업데이트**: 2026-01-26

---

## Quick Start

```kotlin
// 1. 설정 (최초 1회)
Ivm.configure {
    baseUrl = "http://localhost:8080"
    tenantId = "oliveyoung"
}

// 2. 쓰기 - 코드젠으로 생성된 Entities 사용
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C 1000mg"
    price = 15000
}.deploy()

// 3. 읽기 - 코드젠으로 생성된 Views 사용
val view = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .get()

println(view.string("name"))  // "비타민C 1000mg"
```

> 💡 `Entities`와 `Views`는 Contract에서 `./gradlew generateSchema`로 자동 생성됩니다.

---

## 목차

1. [설정](#설정)
2. [쓰기 (Deploy)](#쓰기-deploy)
3. [읽기 (Query)](#읽기-query)
4. [Contract & 코드젠](#contract--코드젠)
5. [API 레퍼런스](#api-레퍼런스)
6. [FAQ](#faq)

---

## 설정

```kotlin
Ivm.configure {
    baseUrl = "http://localhost:8080"   // API 서버 주소
    tenantId = "oliveyoung"             // 기본 테넌트 ID
    timeout = Duration.ofSeconds(30)    // 타임아웃
}
```

---

## 쓰기 (Deploy)

### 기본 패턴 (코드젠 - 추천!)

```kotlin
// 코드젠으로 생성된 Entities 사용
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.deploy()
```

> 💡 `Entities`는 Contract에서 코드젠으로 자동 생성됩니다. [코드젠 섹션](#코드젠) 참고.

### 도메인별 DSL

```kotlin
// Product
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
    category = "건강식품"
    brand = "종근당"
    attribute("weight", "500g")
}.deploy()

// Brand
Ivm.client().ingest(Entities.Brand) {
    brandId = "BRAND-001"
    name = "올리브영"
    logoUrl = "https://..."
}.deploy()

// Category
Ivm.client().ingest(Entities.Category) {
    categoryId = "CAT-001"
    name = "스킨케어"
    parentId = "ROOT"
}.deploy()
```

### 동기/비동기

```kotlin
// 동기 - 모든 단계 완료 후 반환
val result = Ivm.client().ingest().product { ... }.deploy()

// 비동기 - 즉시 반환, 백그라운드 처리
val job = Ivm.client().ingest().product { ... }.deployAsync()
val status = Ivm.client().deploy.await(job.jobId)
```

### 단계별 제어

```kotlin
// 전체 파이프라인
Ivm.client().ingest().product { ... }.deploy()

// 단계별 체이닝
val ingested = Ivm.client().ingest().product { ... }.ingest()
val compiled = ingested.compile()
val shipped = compiled.ship()

// Sink별 동기/비동기 선택
compiled.ship {
    sync { opensearch() }      // 검색: 즉시
    async { personalize() }    // 추천: 백그라운드
}
```

---

## 읽기 (Query)

### 기본 패턴 (타입 세이프)

```kotlin
// 코드젠으로 생성된 Views 사용 (추천)
val view = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .get()

// 데이터 접근
println(view.data)            // 전체 JSON
println(view["core"])         // core slice
println(view.string("name"))  // 특정 필드
```

> 💡 `Views`는 Contract에서 코드젠으로 자동 생성됩니다. [코드젠 섹션](#코드젠) 참고.

### 결과 처리

```kotlin
val view = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .get()

// 성공/실패 체크
if (view.success) {
    println("상품명: ${view.string("name")}")
} else {
    println("에러: ${view.error}")
}

// 예외 던지기
val data = view.orThrow()

// null 반환 (에러 시)
val data = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .getOrNull()

// 존재 여부만 확인
val exists = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .exists()
```

### 고급 옵션

```kotlin
val view = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .options {
        strongConsistency()           // 강한 일관성 (쓰기 직후 읽기)
        projection("core", "pricing") // 특정 Slice만 조회
        noCache()                     // 캐시 무시
        timeout(Duration.ofSeconds(5))
    }
    .get()
```

### 범위 검색

```kotlin
// 기본 범위 검색
val results = Ivm.client().query(Views.Product.pdp)
    .range { keyPrefix("SKU-") }
    .limit(100)
    .list()

results.items.forEach { println(it.entityKey) }

// 필터 조건
val results = Ivm.client().query(Views.Product.pdp)
    .range {
        keyPrefix("SKU-")
        where("category", "스킨케어")
        whereGreaterThan("price", 10000)
    }
    .list()

// 페이지네이션
val page1 = Ivm.client().query(Views.Product.pdp)
    .range { all() }
    .limit(100)
    .list()

if (page1.hasMore) {
    val page2 = Ivm.client().query(Views.Product.pdp)
        .range { all() }
        .after(page1.nextCursor)
        .list()
}

// 자동 페이지네이션 (Sequence)
Ivm.client().query(Views.Product.pdp)
    .range { keyPrefix("SKU-") }
    .stream()
    .take(500)
    .forEach { println(it.entityKey) }
```

### 타입 세이프 결과 (파서 포함)

```kotlin
// 대문자 시작 = 타입 세이프 버전 (결과 타입 보장)
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

// IDE 자동완성 + 타입 보장
println(product.name)        // String
println(product.price)       // Long
println(product.stock)       // Int
println(product.isAvailable) // Boolean

// 타입 세이프 범위 검색
val products = Ivm.client().query(Views.Product.Pdp)
    .range { keyPrefix("SKU-") }
    .list()

products.items.forEach { product: ProductPdpData ->
    println("${product.name}: ${product.price}원")
}
```

---

## Contract & 코드젠

### 개요

Contract(스키마)는 DynamoDB/YAML에 저장되며, 코드젠으로 타입 세이프한 SDK 코드를 자동 생성합니다.

```
Contract (DynamoDB/YAML)  →  코드젠  →  Views.kt + Entities.kt  →  SDK 사용
```

| Contract 종류 | 생성 코드 | 용도 |
|--------------|----------|------|
| `VIEW_DEFINITION` | `Views` | 읽기 (Query) |
| `ENTITY_SCHEMA` | `Entities` | 쓰기 (Ingest) |

### 코드젠 실행

```bash
# 전체 스키마 생성 (Views + Entities)
./gradlew generateSchema

# Views만 생성
./gradlew generateViews

# Entities만 생성
./gradlew generateEntities
```

### 생성되는 코드

```kotlin
// ===== Views.kt (읽기용) =====
object Views {
    object Product {
        val pdp = ViewRef<JsonObject>("product.pdp", listOf("CORE", "PRICING"))
        val search = ViewRef<JsonObject>("product.search", listOf("CORE"))
    }
}

// ===== Entities.kt (쓰기용) =====
object Entities {
    val Product = EntityRef<ProductBuilder>("PRODUCT")
    val Brand = EntityRef<BrandBuilder>("BRAND")
    val Category = EntityRef<CategoryBuilder>("CATEGORY")
}

// ===== ProductBuilder.kt =====
class ProductBuilder : EntityBuilder {
    // 필수 필드
    var sku: String = ""
    var name: String = ""
    var price: Long = 0L
    
    // 선택 필드
    var salePrice: Long? = null
    var category: String? = null
    var brand: String? = null
    var stock: Int? = 0
    
    // 커스텀 속성
    fun attribute(key: String, value: Any)
}
```

### Contract 정의 (YAML)

**View (읽기)**

```yaml
# src/main/resources/contracts/v1/view-product-pdp.v1.yaml
kind: VIEW_DEFINITION
id: view.product.pdp.v1
version: 1.0.0
status: ACTIVE

requiredSlices:
  - CORE
  - PRICING

optionalSlices:
  - INVENTORY
  - PROMOTION
```

**Entity (쓰기)**

```yaml
# src/main/resources/contracts/v1/entity-product.v1.yaml
kind: ENTITY_SCHEMA
id: entity.product.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

fields:
  - name: sku
    type: string
    required: true
    
  - name: name
    type: string
    required: true
    
  - name: price
    type: long
    required: true
    
  - name: salePrice
    type: long
    required: false
    
  - name: category
    type: string
    required: false
```

### Contract 관리 (런타임)

```kotlin
// Contract 조회
val contract = registry.loadViewDefinitionContract(
    ContractRef("view.product.pdp.v1", SemVer.parse("1.0.0"))
)

// Contract 목록 조회
val contracts = registry.listViewDefinitions(ContractStatus.ACTIVE)

// Contract 저장
registry.saveViewDefinitionContract(contract)
```

---

## API 레퍼런스

### Ivm

| 메서드 | 설명 |
|--------|------|
| `Ivm.configure { }` | SDK 설정 |
| `Ivm.client()` | Client API 진입점 |

### IvmClient

| 메서드 | 설명 |
|--------|------|
| `.ingest()` | 쓰기 컨텍스트 시작 |
| `.query(viewRef)` | 읽기 (타입 세이프) |
| `.query(viewId)` | 읽기 (문자열) |
| `.deploy.status(jobId)` | Job 상태 조회 |
| `.deploy.await(jobId)` | Job 완료 대기 |

### IngestContext

| 메서드 | 설명 |
|--------|------|
| `.product { }` | Product DSL |
| `.brand { }` | Brand DSL |
| `.category { }` | Category DSL |

### DeployableContext

| 메서드 | 반환 | 설명 |
|--------|------|------|
| `.deploy()` | `DeployResult` | 동기 배포 |
| `.deployAsync()` | `DeployJob` | 비동기 배포 |
| `.ingest()` | `IngestedEntity` | Ingest만 |
| `.explain()` | `DeployPlan` | Dry Run |

### QueryBuilder

| 메서드 | 설명 |
|--------|------|
| `.key(entityKey)` | 키 설정 (필수) |
| `.tenant(id)` | 테넌트 설정 |
| `.version(v)` | 버전 설정 |
| `.options { }` | 고급 옵션 |
| `.get()` | 단일 조회 |
| `.getOrNull()` | 조회 또는 null |
| `.exists()` | 존재 여부 |
| `.range { }` | 범위 검색 조건 |
| `.limit(n)` | 결과 제한 |
| `.after(cursor)` | 페이지네이션 |
| `.list()` | 범위 검색 실행 |
| `.stream()` | 자동 페이지네이션 |
| `.count()` | 개수만 조회 |

### RangeBuilder

| 메서드 | 설명 |
|--------|------|
| `all()` | 전체 조회 |
| `keyPrefix(prefix)` | Key prefix 검색 |
| `keyBetween(from, to)` | Key 범위 |
| `versionBetween(from, to)` | 버전 범위 |
| `where(field, value)` | 필터 (=) |
| `whereGreaterThan(field, value)` | 필터 (>) |
| `whereLessThan(field, value)` | 필터 (<) |
| `whereIn(field, values)` | 필터 (IN) |
| `whereContains(field, str)` | 필터 (CONTAINS) |

### QueryOptions

| 옵션 | 설명 |
|------|------|
| `strongConsistency()` | 강한 일관성 |
| `projection(slices...)` | 특정 Slice만 |
| `noCache()` | 캐시 무시 |
| `timeout(duration)` | 타임아웃 |
| `cache(enabled, ttl)` | 캐시 설정 |

### ViewResult

| 속성/메서드 | 설명 |
|------------|------|
| `success` | 성공 여부 |
| `data` | 전체 데이터 (JsonObject) |
| `error` | 에러 메시지 |
| `[sliceType]` | Slice 데이터 접근 |
| `string(path)` | 문자열 필드 |
| `long(path)` | 숫자 필드 |
| `orThrow()` | 에러 시 예외 |

### QueryResultPage

| 속성 | 설명 |
|------|------|
| `items` | 결과 목록 |
| `totalCount` | 전체 개수 |
| `hasMore` | 다음 페이지 존재 |
| `nextCursor` | 다음 페이지 커서 |

---

## FAQ

### Q: 동기와 비동기 중 뭘 써야 하나요?

- **동기 (`.deploy()`)**: API 응답에 결과가 필요한 경우
- **비동기 (`.deployAsync()`)**: 빠른 응답이 중요하고 결과는 나중에 확인해도 되는 경우

### Q: 코드젠은 언제 실행하나요?

- **로컬 개발**: `./gradlew generateViews`
- **CI/CD**: 빌드 시 자동 실행
- **운영 환경**: `ViewCodeGen.generateFromDynamoDB()` 사용

### Q: 쓰기 직후 바로 읽어야 하면?

```kotlin
// 강한 일관성 옵션 사용
val view = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .options { strongConsistency() }
    .get()
```

### Q: 캐시는 어떻게 동작하나요?

기본 5분 TTL 캐시 활성화:
- `noCache()`: 캐시 무시
- `cache(true, Duration.ofMinutes(10))`: TTL 커스터마이징

### Q: Contract는 어디에 저장되나요?

DynamoDB `contract_registry` 테이블에 저장됩니다. GSI `kind-status-index`로 목록 조회 가능.

### Q: 타입 세이프 결과를 사용하려면?

대문자로 시작하는 ViewRef 사용:

```kotlin
// JsonObject 반환
val view = Ivm.client().query(Views.Product.pdp).key("SKU-001").get()

// ProductPdpData 반환 (타입 세이프)
val product = Ivm.client().query(Views.Product.Pdp).key("SKU-001").get()
```

---

## 부록: 전체 시나리오 예제

```kotlin
// ===== 설정 =====
Ivm.configure {
    baseUrl = "http://localhost:8080"
    tenantId = "oliveyoung"
}

// ===== 쓰기 (Deploy) - 코드젠 Entities 사용 =====

// 시나리오 1: 상품 등록
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.deploy()

// 시나리오 2: 검색은 즉시, 추천은 배치
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-002"
    name = "비타민D"
    price = 20000
}.deploy {
    compile { sync() }
    ship {
        sync { opensearch() }
        async { personalize() }
    }
}

// 시나리오 3: 비동기 처리
val job = Ivm.client().ingest(Entities.Product) {
    sku = "SKU-003"
    name = "비타민E"
    price = 25000
}.deployAsync()
// API 응답: { "jobId": "job-123", "status": "QUEUED" }

// ===== 읽기 (Query) - 코드젠 Views 사용 =====

// 시나리오 4: 상품 조회
val product = Ivm.client().query(Views.Product.pdp)
    .key("SKU-001")
    .get()
println("상품명: ${product.string("name")}")

// 시나리오 5: 범위 검색
val results = Ivm.client().query(Views.Product.pdp)
    .range { keyPrefix("SKU-") }
    .limit(100)
    .list()

results.items.forEach { 
    println("${it.entityKey}: ${it.string("name")}")
}

// 시나리오 6: 타입 세이프 조회
val typedProduct: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
println("${typedProduct.name}: ${typedProduct.price}원")
```

---

**문의**: SDK 관련 문의는 #ivm-sdk 채널로 연락주세요.
