# IVM SDK 사용 가이드

> **버전**: 2.2.0  
> **최종 업데이트**: 2026-01-26  
> **E2E 테스트**: 157개 시나리오 검증 완료 (Fanout 80개 포함)

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

// 3. 읽기 - 코드젠으로 생성된 Views 사용 (타입 세이프)
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

println(product.name)  // "비타민C 1000mg" (IDE 자동완성 지원)
```

> 💡 `Entities`와 `Views`는 Contract에서 `./gradlew generateSchema`로 자동 생성됩니다.

### 키/ID 규칙

| 용어 | 설명 | 예시 |
|------|------|------|
| **Business Key** | 도메인에서 사용하는 식별자. SDK API에서 `.key()`로 전달 | `SKU-001`, `BR-001`, `CAT-001` |
| **EntityKey** | 내부 저장/조회용 정규화 키. SDK가 자동 생성하므로 직접 사용 불필요 | (내부 전용) |
| **ViewId** | 뷰 식별자 | `product.pdp`, `product.search` |
| **ContractRef** | 계약 식별자 (버전 포함) | `view.product.pdp.v1@1.0.0` |

> 💡 `.key("SKU-001")`는 **비즈니스 키**를 받으며, SDK가 내부적으로 EntityKey로 정규화합니다.  
> 일반 앱 개발에서 EntityKey를 직접 다룰 일은 없습니다.

---

## 목차

1. [설정](#설정)
2. [쓰기 (Deploy)](#쓰기-deploy)
3. [Fanout (자동 전파)](#fanout-자동-전파)
4. [읽기 (Query)](#읽기-query)
5. [Contract & 코드젠](#contract--코드젠)
6. [API 레퍼런스](#api-레퍼런스)
7. [플랫폼 운영자 API](#플랫폼-운영자-api)
8. [에러 처리](#에러-처리)
9. [FAQ](#faq)
10. [E2E 검증 현황](#e2e-검증-현황)

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
    brandId = "BR-001"
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

// Job 완료 대기 (폴링)
val status = Ivm.client().deploy.await(job.jobId)

// Job 상태만 조회
val jobStatus = Ivm.client().deploy.status(job.jobId)
```

### 단계별 제어

```kotlin
// 전체 파이프라인 (한 번에)
Ivm.client().ingest().product { ... }.deploy()

// 단계별 체이닝
val ingested = Ivm.client().ingest().product { ... }.ingest()
// IngestedEntity: entityKey, version 접근 가능

val compiled = ingested.compile()
// CompiledEntity: entityKey, version, slices 접근 가능

val shipped = compiled.ship()
// ShippedEntity: entityKey, version, sinks 접근 가능

// 비동기 단계별 체이닝
val job1 = ingested.compileAsync()  // DeployJob 반환
val job2 = ingested.compileAndShipAsync()  // DeployJob 반환 (compile + ship)

// Sink별 동기/비동기 선택 (혼합 모드)
val mixedResult = compiled.ship {
    sync { opensearch { index("products") } }      // 검색: 즉시
    async { personalize { dataset("recs") } }      // 추천: 백그라운드
}
// ShipMixedResult: syncSinks, asyncJob 접근 가능
```

### Deploy API 비교

| 메서드 | 동작 | 반환 | 결과 조회 |
|--------|------|------|-----------|
| `deploy()` | ingest+compile+ship **모두 동기** | `DeployResult` | 즉시 결과 확인 |
| `deployAsync()` | ingest+compile+ship **모두 비동기** | `DeployJob` | `deploy.await(jobId)` |
| `deployNow()` | compile **동기** + ship **비동기** | `DeployResult` | 검색 즉시 반영 패턴 |
| `deployNowAndShipNow()` | compile+ship **모두 동기** | `DeployResult` | 즉시 결과 확인 |
| `deployQueued()` | compile+ship **모두 비동기 큐** | `DeployJob` | `deploy.await(jobId)` |

```kotlin
// 동기 - 완료까지 대기 후 결과 반환
val result = Ivm.client().ingest(Entities.Product) { ... }.deploy()

// 비동기 - 즉시 Job 반환, 나중에 결과 조회
val job = Ivm.client().ingest(Entities.Product) { ... }.deployAsync()
val result = Ivm.client().deploy.await(job.jobId)  // 완료 대기

// 검색 즉시 반영 (compile 동기, ship 비동기)
Ivm.client().ingest(Entities.Product) { ... }.deployNow {
    opensearch { index("products") }
}

// 전체 동기 (compile + ship 모두 동기)
Ivm.client().ingest(Entities.Product) { ... }.deployNowAndShipNow {
    opensearch { index("products") }
}

// 전체 비동기 큐 (배치 처리용)
val job = Ivm.client().ingest(Entities.Product) { ... }.deployQueued {
    opensearch { index("products") }
}
```

---

## Fanout (자동 전파)

Brand, Category 같은 **상위 엔티티**가 업데이트되면, 이를 참조하는 **하위 엔티티**(Product)가 **자동으로 재슬라이싱**됩니다.

### 동작 원리

```
Brand 업데이트 → Outbox 이벤트 발행 → Fanout 자동 실행 → 연관 Product 재슬라이싱
```

### 사용법 (앱 개발자)

**별도 코드 없이 자동으로 동작합니다.** SDK로 엔티티를 업데이트하면 연관 엔티티가 자동 재슬라이싱됩니다.

```kotlin
// Brand 업데이트 → 이 Brand를 참조하는 모든 Product 자동 재슬라이싱
Ivm.client().ingest(Entities.Brand) {
    brandId = "BR-001"
    name = "이니스프리 (수정됨)"
    logoUrl = "https://..."
}.deploy()

// 위 코드 실행 후 자동으로:
// 1. Brand 데이터 저장
// 2. "BR-001"을 참조하는 모든 Product 감지
// 3. 해당 Product들 재슬라이싱 (brandName 필드 업데이트됨)
```

### 필수 조건: Contract 설정

Fanout이 동작하려면 RuleSet에 `joins`가 정의되어 있어야 합니다.

```yaml
# contracts/ruleset.core.v1.yaml
kind: RULE_SET
metadata:
  id: ruleset.core.v1
  version: 1.0.0

spec:
  entityType: product
  
  # Join 정의 → Fanout 의존성 자동 추론
  joins:
    - sourceSlice: CORE
      targetEntity: brand      # Brand 변경 시 Product fanout
      joinPath: /brandCode
      cardinality: MANY_TO_ONE
    
    - sourceSlice: CORE
      targetEntity: category   # Category 변경 시 Product fanout
      joinPath: /categoryCode
      cardinality: MANY_TO_ONE
```

> 💡 **핵심**: RuleSet에 `joins`가 정의되어 있으면 upstream 엔티티(Brand, Category) 변경 시 **자동으로** downstream 엔티티(Product)가 재슬라이싱됩니다.

> ⚙️ 수동 Fanout 트리거, 설정 커스터마이징, 메트릭 모니터링은 [플랫폼 운영자 API](#플랫폼-운영자-api) 섹션을 참고하세요.

---

## 읽기 (Query)

### 기본 패턴 (타입 세이프)

```kotlin
// 코드젠으로 생성된 Views 사용 (추천) - 타입 세이프
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

// IDE 자동완성 + 타입 보장
println(product.name)        // String
println(product.price)       // Long
println(product.stock)       // Int
println(product.isAvailable) // Boolean
```

> 💡 `Views`는 Contract에서 코드젠으로 자동 생성됩니다. 모든 View는 대문자로 시작하며 타입 세이프한 결과를 반환합니다. [코드젠 섹션](#코드젠) 참고.

### 결과 처리

```kotlin
// 타입 세이프 조회 (권장)
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

println("상품명: ${product.name}")
println("가격: ${product.price}원")

// null 반환 (에러 시)
val nullableProduct = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .getOrNull()

if (nullableProduct != null) {
    println("상품명: ${nullableProduct.name}")
}

// 존재 여부만 확인 (데이터 로드 없음)
val exists = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .exists()

// 변환 (성공 시만)
val productInfo = view.map { json ->
    "${json["name"]}: ${json["price"]}원"
}

// 변환 또는 기본값
val productName = view.mapOrDefault("Unknown") { json ->
    json["name"]?.jsonPrimitive?.content ?: "Unknown"
}

// 변환 또는 예외 (실패 시 ViewQueryException)
val productInfo = view.mapOrThrow { json ->
    "${json["name"]?.jsonPrimitive?.content}: ${json["price"]?.jsonPrimitive?.long}원"
}
```

### 고급 옵션

```kotlin
val view = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .options {
        strongConsistency()           // 강한 일관성 (쓰기 직후 읽기)
        projection("core", "pricing") // 특정 Slice만 조회
        noCache()                     // 캐시 무시
        timeout(Duration.ofSeconds(5))
        retry(enabled = true, maxRetries = 3)
    }
    .get()
```

### 범위 검색

```kotlin
// 기본 범위 검색
val results = Ivm.client().query(Views.Product.Pdp)
    .tenant("oliveyoung")
    .range { keyPrefix("SKU-") }
    .limit(100)
    .list()

results.items.forEach { println(it.entityKey) }

// 필터 조건
val filtered = Ivm.client().query(Views.Product.Pdp)
    .range {
        keyPrefix("SKU-")
        where("category", "스킨케어")
        whereGreaterThan("price", 10000)
        whereLessThan("price", 50000)
        whereIn("brand", listOf("라네즈", "설화수"))
        whereContains("name", "크림")
    }
    .list()

// 페이지네이션
val page1 = Ivm.client().query(Views.Product.Pdp)
    .range { all() }
    .limit(100)
    .list()

if (page1.hasMore) {
    val page2 = Ivm.client().query(Views.Product.Pdp)
        .range { all() }
        .after(page1.nextCursor)
        .list()
}

// 자동 페이지네이션 (Sequence) - Lazy Evaluation
Ivm.client().query(Views.Product.Pdp)
    .range { keyPrefix("SKU-") }
    .stream()
    .take(500)
    .forEach { println(it.entityKey) }

// 정렬
val sorted = Ivm.client().query(Views.Product.Pdp)
    .range { keyPrefix("SKU-") }
    .descending()  // 또는 .ascending()
    .list()

// 첫 번째 결과만
val first = Ivm.client().query(Views.Product.Pdp)
    .range { keyPrefix("SKU-") }
    .first()

// 개수만 조회
val count = Ivm.client().query(Views.Product.Pdp)
    .range { keyPrefix("SKU-") }
    .count()
```

### 타입 세이프 결과

모든 View는 **대문자로 시작**하며 **타입 세이프한 결과**를 반환합니다.

```kotlin
// 타입 세이프 조회 - ProductPdpData 반환 (IDE 자동완성 지원)
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

> 💡 모든 View는 코드젠으로 자동 생성되며, 타입 세이프한 데이터 클래스가 함께 생성됩니다.

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

#### Views 네이밍 규칙

모든 View는 **대문자로 시작**하며 **타입 세이프한 결과**를 반환합니다.

| View | 반환 타입 | 설명 |
|------|-----------|------|
| `Views.Product.Pdp` | `ProductPdpData` | 상품 상세 페이지 |
| `Views.Product.Search` | `ProductSearchData` | 검색 결과 |
| `Views.Product.Cart` | `ProductCartData` | 장바구니 |
| `Views.Brand.Detail` | `BrandDetailData` | 브랜드 상세 |

> 💡 코드젠은 View별 **데이터 클래스**를 자동 생성합니다. IDE 자동완성과 타입 체크가 지원됩니다.

```kotlin
// ===== Views.kt (읽기용) =====
object Views {
    object Product {
        // 모든 View는 대문자로 시작, 타입 세이프 반환
        object Pdp : ViewRef<ProductPdpData>(...)
        object Search : ViewRef<ProductSearchData>(...)
        object Cart : ViewRef<ProductCartData>(...)
    }
    
    object Brand {
        object Detail : ViewRef<BrandDetailData>(...)
        object List : ViewRef<BrandListData>(...)
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

| 메서드/속성 | 설명 |
|------------|------|
| `.ingest()` | 쓰기 컨텍스트 시작 |
| `.ingest(entityRef) { }` | 코드젠 엔티티로 Ingest (추천) |
| `.query(viewRef)` | 읽기 (타입 세이프) |
| `.query(viewId)` | 읽기 (문자열) |
| `.queries` | QueryApi 네임스페이스 |
| `.deploy` | DeployStatusApi 네임스페이스 |
| `.plan` | PlanExplainApi 네임스페이스 |

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
| `.deployNow { }` | `DeployResult` | compile.sync + ship.async |
| `.deployNowAndShipNow { }` | `DeployResult` | compile.sync + ship.sync |
| `.deployQueued { }` | `DeployJob` | 전체 비동기 |
| `.ingest()` | `IngestedEntity` | Ingest만 |
| `.explain()` | `DeployPlan` | Dry Run (실행 계획 미리보기) |

### IngestedEntity

| 메서드/속성 | 설명 |
|------------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `.compile()` | 컴파일 실행 → CompiledEntity |
| `.compileAsync()` | 비동기 컴파일 → DeployJob |
| `.compileAndShip()` | 컴파일 + Ship → ShippedEntity |
| `.compileAndShipAsync()` | 컴파일 + Ship (비동기) → DeployJob |

### CompiledEntity

| 메서드/속성 | 설명 |
|------------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `slices` | 생성된 Slice 목록 |
| `.ship()` | Ship 실행 → ShippedEntity |
| `.shipAsync()` | 비동기 Ship → DeployJob |
| `.ship { sync{} async{} }` | Sink별 혼합 → ShipMixedResult |

### ShippedEntity

| 메서드/속성 | 설명 |
|------------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `sinks` | 전송된 Sink 목록 |
| `success` | 전송 성공 여부 |
| `error` | 에러 메시지 (실패 시) |
| `.toDeployResult()` | DeployResult로 변환 |

### ShipMixedResult

| 속성/메서드 | 설명 |
|------------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `syncSinks` | 동기 처리된 Sink 목록 |
| `success` | 동기 처리 성공 여부 |
| `asyncJob` | 비동기 Job (DeployJob?) |

### QueryBuilder

| 메서드 | 설명 |
|--------|------|
| `.key(entityKey)` | 키 설정 (필수) |
| `.tenant(id)` | 테넌트 설정 |
| `.version(v)` | 버전 설정 |
| `.latest()` | 최신 버전 (명시적) |
| `.options { }` | 고급 옵션 |
| `.get()` | 단일 조회 |
| `.getAsync()` | 비동기 조회 |
| `.getOrNull()` | 조회 또는 null |
| `.getOrDefault(default)` | 조회 또는 기본값 |
| `.exists()` | 존재 여부 |
| `.range { }` | 범위 검색 조건 |
| `.limit(n)` | 결과 제한 (1-1000) |
| `.after(cursor)` | 페이지네이션 |
| `.orderBy(order)` | 정렬 순서 |
| `.ascending()` | 오름차순 |
| `.descending()` | 내림차순 |
| `.list()` | 범위 검색 실행 |
| `.listAsync()` | 비동기 범위 검색 |
| `.stream()` | 자동 페이지네이션 (Sequence) |
| `.count()` | 개수만 조회 |
| `.first()` | 첫 번째 결과 |
| `.firstOrThrow()` | 첫 번째 또는 예외 |

### TypedQueryBuilder (타입 세이프)

| 메서드 | 설명 |
|--------|------|
| `.tenant(id)` | 테넌트 설정 |
| `.key(entityKey)` | 키 설정 (필수) |
| `.version(v)` | 버전 설정 |
| `.latest()` | 최신 버전 |
| `.range { }` | 범위 검색 조건 |
| `.limit(n)` | 결과 제한 |
| `.after(cursor)` | 페이지네이션 |
| `.orderBy(order)` | 정렬 순서 |
| `.ascending()` | 오름차순 |
| `.descending()` | 내림차순 |
| `.options { }` | 고급 옵션 |
| `.get()` | 단일 조회 (타입 T 반환) |
| `.getOrNull()` | 조회 또는 null |
| `.getOrDefault(default)` | 조회 또는 기본값 |
| `.exists()` | 존재 여부 |
| `.list()` | 범위 검색 실행 (TypedQueryResultPage<T>) |
| `.stream()` | 자동 페이지네이션 (Sequence<T>) |
| `.count()` | 개수만 조회 |
| `.first()` | 첫 번째 결과 |
| `.firstOrThrow()` | 첫 번째 또는 예외 |

### RangeBuilder

| 메서드 | 설명 |
|--------|------|
| `all()` | 전체 조회 |
| `keyPrefix(prefix)` | Key prefix 검색 |
| `keyBetween(from, to)` | Key 범위 |
| `keyFrom(from)` | Key >= from |
| `keyTo(to)` | Key <= to |
| `versionBetween(from, to)` | 버전 범위 |
| `versionFrom(from)` | 버전 >= from |
| `versionTo(to)` | 버전 <= to |
| `latestOnly()` | 최신 버전만 조회 |
| `where(field, value)` | 필터 (=) |
| `where(field, op, value)` | 필터 (커스텀 연산자) |
| `whereGreaterThan(field, value)` | 필터 (>) |
| `whereLessThan(field, value)` | 필터 (<) |
| `whereIn(field, values)` | 필터 (IN) |
| `whereContains(field, str)` | 필터 (CONTAINS) |

### QueryOptions

| 옵션 | 설명 |
|------|------|
| `strongConsistency()` | 강한 일관성 |
| `consistency(level)` | 일관성 레벨 설정 |
| `projection(slices...)` | 특정 Slice만 |
| `noCache()` | 캐시 무시 |
| `cacheOnly()` | 캐시만 조회 |
| `cache(enabled, ttl)` | 캐시 설정 |
| `timeout(duration)` | 타임아웃 |
| `retry(enabled, maxRetries)` | 재시도 설정 |
| `noRetry()` | 재시도 안 함 |
| `includeMetadata()` | 메타데이터 포함 |

### ViewResult

| 속성/메서드 | 설명 |
|------------|------|
| `success` | 성공 여부 |
| `viewId` | View ID |
| `tenantId` | 테넌트 ID |
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `data` | 전체 데이터 (JsonObject) |
| `error` | 에러 메시지 |
| `errorCode` | 에러 코드 |
| `meta` | 메타데이터 |
| `[sliceType]` | Slice 데이터 접근 (대소문자 무관) |
| `string(path)` | 문자열 필드 (dot notation 지원) |
| `long(path)` | 숫자 필드 |
| `has(path)` | 필드 존재 여부 |
| `map { }` | 변환 (성공 시만) |
| `mapOrDefault(default) { }` | 변환 또는 기본값 |
| `mapOrThrow { }` | 변환 또는 예외 |
| `orThrow()` | 에러 시 ViewQueryException |

### QueryResultPage

| 속성 | 설명 |
|------|------|
| `items` | 결과 목록 |
| `totalCount` | 전체 개수 (추정) |
| `hasMore` | 다음 페이지 존재 |
| `nextCursor` | 다음 페이지 커서 |
| `queryTimeMs` | 쿼리 소요 시간 |
| `isEmpty` | 비어있는지 |
| `size` | 결과 개수 |
| `first` | 첫 번째 결과 |
| `last` | 마지막 결과 |

### 모델 클래스

#### DeployResult

| 속성 | 설명 |
|------|------|
| `success` | 성공 여부 |
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `error` | 에러 메시지 (실패 시) |

#### DeployJob

| 속성 | 설명 |
|------|------|
| `jobId` | Job ID |
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `state` | 상태 (QUEUED, RUNNING, DONE, FAILED 등) |

#### DeployJobStatus

| 속성 | 설명 |
|------|------|
| `jobId` | Job ID |
| `state` | 상태 |
| `createdAt` | 생성 시간 |
| `updatedAt` | 업데이트 시간 |
| `error` | 에러 메시지 (실패 시) |

#### IngestResult

| 속성 | 설명 |
|------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `success` | 성공 여부 |

#### CompileResult

| 속성 | 설명 |
|------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `slices` | 생성된 Slice 목록 |
| `success` | 성공 여부 |

#### ShipResult

| 속성 | 설명 |
|------|------|
| `entityKey` | 엔티티 키 |
| `version` | 버전 |
| `sinks` | 전송된 Sink 목록 |
| `success` | 성공 여부 |
| `error` | 에러 메시지 (실패 시) |

### DeployStatusApi

| 메서드 | 설명 |
|--------|------|
| `status(jobId)` | Job 상태 조회 |
| `await(jobId, timeout, pollInterval)` | Job 완료 대기 (기본: timeout=5분, pollInterval=1초) |

### PlanExplainApi

| 메서드 | 설명 |
|--------|------|
| `explainLastPlan(deployId)` | 마지막 Deploy Plan 설명 조회 (Dry Run) |

### ViewResult.Meta

| 속성 | 설명 |
|------|------|
| `slicesUsed` | 사용된 Slice 목록 |
| `missingSlices` | 누락된 Slice 목록 (partial 응답 시) |
| `contractsUsed` | 사용된 Contract 버전 목록 |
| `queryTimeMs` | 쿼리 소요 시간 (ms) |
| `fromCache` | 캐시 히트 여부 |
| `consistency` | 적용된 일관성 레벨 |

---

## 플랫폼 운영자 API

> ⚠️ 이 섹션은 **플랫폼 운영자/인프라 팀**을 위한 내용입니다. 일반 앱 개발자는 이 API를 직접 사용할 필요가 없습니다.

### 수동 Fanout 트리거

특수한 상황에서 Fanout을 수동으로 트리거해야 할 때 사용합니다.

```kotlin
// Admin API 사용 (권장)
Ivm.admin().fanout().trigger(
    entityType = "brand",
    businessKey = "BR-001",
    version = 5L,
)

// 또는 FanoutWorkflow 직접 호출 (내부용)
val fanoutWorkflow: FanoutWorkflow = koin.get()
val result = fanoutWorkflow.onEntityChange(
    tenantId = TenantId("oliveyoung"),
    upstreamEntityType = "brand",
    upstreamEntityKey = Entities.Brand.toEntityKey("BR-001"),
    upstreamVersion = 5L,
)

when (result) {
    is Result.Ok -> println("처리: ${result.value.processedCount}개")
    is Result.Err -> println("에러: ${result.error}")
}
```

### Fanout 설정 커스터마이징

```kotlin
// 프리셋 사용
val config = FanoutConfig.HIGH_THROUGHPUT  // 대량 처리용
val config = FanoutConfig.CONSERVATIVE     // 안정성 우선

// 커스텀 설정
val customConfig = FanoutConfig(
    batchSize = 50,                              // 한 번에 50개씩
    batchDelay = 100.milliseconds,               // 배치 간 딜레이
    maxFanout = 5000,                            // 최대 5000개까지
    circuitBreakerAction = CircuitBreakerAction.SKIP,
    timeout = 10.minutes,
    deduplicationWindow = 5.seconds,
    maxConcurrentFanouts = 5,
    retry = RetryConfig(maxAttempts = 3, initialDelay = 1.seconds),
)
```

#### FanoutConfig 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `enabled` | `true` | Fanout 활성화 여부 |
| `batchSize` | `100` | 한 번에 처리할 엔티티 수 |
| `batchDelay` | `50ms` | 배치 간 딜레이 (backpressure) |
| `maxFanout` | `10000` | 최대 처리 가능 엔티티 수 (circuit breaker) |
| `circuitBreakerAction` | `SKIP` | 초과 시 행동 (`SKIP`, `ERROR`, `ASYNC`) |
| `priority` | `NORMAL` | 우선순위 (`LOW`, `NORMAL`, `HIGH`, `CRITICAL`) |
| `maxConcurrentFanouts` | `10` | 동시 실행 Job 수 제한 |
| `timeout` | `5분` | Job 타임아웃 |
| `retry.maxAttempts` | `3` | 최대 재시도 횟수 |
| `retry.initialDelay` | `1초` | 초기 재시도 딜레이 |
| `deduplicationWindow` | `1초` | 중복 요청 방지 윈도우 |
| `targetSliceTypes` | `null` | 특정 SliceType만 재생성 (null=전체) |

### Fanout 메트릭 모니터링

```kotlin
val metrics = fanoutWorkflow.getMetrics()

println("""
    총 fanout 수: ${metrics.totalFanoutCount}
    성공: ${metrics.successCount}
    실패: ${metrics.failedCount}
    스킵: ${metrics.skippedCount}
    현재 진행 중: ${metrics.activeJobCount}
""")

// 현재 활성 Job 확인
val activeJobs = fanoutWorkflow.getActiveJobs()
activeJobs.forEach { job ->
    println("${job.id}: ${job.progress * 100}% (${job.processedCount}/${job.totalAffected})")
}
```

---

## 에러 처리

### 에러 코드

| 코드 | 설명 | 대응 |
|------|------|------|
| `NOT_FOUND` | 엔티티/뷰 없음 | 키 확인, 데이터 존재 여부 확인 |
| `CONTRACT_MISMATCH` | 요구 Slice 누락 | Contract 정의 확인, 재슬라이싱 필요 |
| `VALIDATION_ERROR` | 입력값 검증 실패 | 요청 파라미터 확인 |
| `TIMEOUT` | 요청 타임아웃 | 재시도 또는 타임아웃 설정 조정 |
| `FANOUT_CIRCUIT_BREAKER` | Fanout 대상 초과로 스킵됨 | 정상 동작, 로그 확인 |

### Query Miss (데이터 없음)

```kotlin
val view = Ivm.client().query(Views.Product.Pdp)
    .key("NONEXISTENT-SKU")
    .get()

if (!view.success) {
    println("에러 코드: ${view.errorCode}")  // "NOT_FOUND"
    println("에러 메시지: ${view.error}")     // "Entity not found: NONEXISTENT-SKU"
}

// 또는 null 반환
val nullableView = Ivm.client().query(Views.Product.Pdp)
    .key("NONEXISTENT-SKU")
    .getOrNull()  // null 반환

// 또는 기본값
val defaultView = Ivm.client().query(Views.Product.Pdp)
    .key("NONEXISTENT-SKU")
    .getOrDefault(defaultProduct)
```

### Contract Mismatch (Slice 누락)

```kotlin
val view = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

if (!view.success && view.errorCode == "CONTRACT_MISMATCH") {
    println("누락된 Slice: ${view.meta?.missingSlices}")
    // → 재슬라이싱 필요
}

// 부분 응답 허용 (일부 Slice 누락 시에도 결과 반환)
val partialView = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .options { allowPartial() }
    .get()

if (partialView.success) {
    println("사용된 Slice: ${partialView.meta?.slicesUsed}")
    println("누락된 Slice: ${partialView.meta?.missingSlices}")
}
```

### Fanout Circuit Breaker

```kotlin
// Fanout 대상이 maxFanout을 초과하면 circuit breaker 동작

// 결과 확인
val result = fanoutWorkflow.onEntityChange(...)
when (result) {
    is Result.Ok -> {
        if (result.value.skippedCount > 0) {
            println("Circuit breaker로 ${result.value.skippedCount}개 스킵됨")
            // 정상 동작 - 시스템 보호를 위해 일부 스킵
        }
    }
    is Result.Err -> {
        if (result.error is DomainError.CircuitBreakerTripped) {
            println("전체 fanout 중단됨 (circuitBreakerAction=ERROR 설정 시)")
        }
    }
}
```

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
val view = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .options { strongConsistency() }
    .get()
```

### Q: 캐시는 어떻게 동작하나요?

기본 5분 TTL 캐시 활성화:
- `noCache()`: 캐시 무시
- `cache(true, Duration.ofMinutes(10))`: TTL 커스터마이징
- `cacheOnly()`: 캐시만 조회 (DB 안 감)

### Q: Contract는 어디에 저장되나요?

DynamoDB `contract_registry` 테이블에 저장됩니다. GSI `kind-status-index`로 목록 조회 가능.

### Q: 타입 세이프 결과를 사용하려면?

모든 View는 대문자로 시작하며 **기본적으로 타입 세이프**합니다:

```kotlin
// 타입 세이프 - ProductPdpData 반환 (IDE 자동완성 지원)
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

println(product.name)   // String
println(product.price)  // Long
```


### Q: Deploy Plan을 미리 확인하려면?

```kotlin
// Dry Run: 실제 배포 없이 계획만 확인
val plan = Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.explain()

println("활성화될 규칙: ${plan.activatedRules}")
println("실행 단계: ${plan.executionSteps}")

// 또는 마지막 배포 계획 조회
val lastPlan = Ivm.client().plan.explainLastPlan("deploy-123")
```

### Q: 단계별로 실행하고 싶으면?

```kotlin
// 1. Ingest만
val ingested = Ivm.client().ingest().product { ... }.ingest()
println("Ingested: ${ingested.entityKey} v${ingested.version}")

// 2. Compile만
val compiled = ingested.compile()
println("Compiled slices: ${compiled.slices}")

// 3. Ship (혼합 모드)
val result = compiled.ship {
    sync { opensearch { index("products") } }
    async { personalize { dataset("recs") } }
}
```

### Q: Brand/Category 변경 시 Product가 자동 업데이트되나요?

네, **Fanout 워크플로우**가 자동으로 처리합니다. 별도 코드 없이 자동 동작합니다.

```kotlin
// Brand 업데이트하면 자동으로 연관 Product 재슬라이싱
Ivm.client().ingest(Entities.Brand) {
    brandId = "BR-001"
    name = "이니스프리 (변경됨)"
}.deploy()
```

RuleSet에 `joins`가 정의되어 있으면 자동으로 동작합니다. 자세한 내용은 [Fanout (자동 전파)](#fanout-자동-전파) 참고.

### Q: Fanout이 너무 많으면 어떻게 되나요?

기본 설정으로 Circuit breaker가 시스템을 보호합니다 (maxFanout=10000, 초과 시 스킵).

커스터마이징이 필요하면 [플랫폼 운영자 API](#플랫폼-운영자-api) 섹션을 참고하세요.

### Q: Fanout 진행 상황을 모니터링하려면?

[플랫폼 운영자 API - Fanout 메트릭 모니터링](#fanout-메트릭-모니터링)을 참고하세요.

```kotlin
val metrics = Ivm.admin().fanout().getMetrics()
println("성공: ${metrics.successCount}, 실패: ${metrics.failedCount}")
```

---

## E2E 검증 현황

> 모든 SDK 시나리오는 E2E 테스트로 검증되었습니다.

| 카테고리 | 테스트 수 | 상태 |
|----------|----------|------|
| 기본 플로우 (Ingest → Slice → Query) | 15+ | ✅ |
| 동기/비동기 배포 | 5+ | ✅ |
| 단계별 체이닝 | 4+ | ✅ |
| Job await | 3+ | ✅ |
| Sink 혼합 모드 | 4+ | ✅ |
| Query 고급 옵션 | 6+ | ✅ |
| 범위 검색/페이지네이션 | 5+ | ✅ |
| stream() 자동 페이지네이션 | 2+ | ✅ |
| ViewResult 편의 메서드 | 10+ | ✅ |
| 에러 핸들링 | 5+ | ✅ |
| 멀티 테넌트 격리 | 3+ | ✅ |
| DynamoDB 저장 | 4+ | ✅ |
| **Fanout 워크플로우** | **80+** | ✅ |
| **합계** | **157+** | ✅ |

### Fanout 테스트 상세

| 카테고리 | 테스트 수 | 설명 |
|----------|----------|------|
| 기본 기능 | 10+ | 단일/다중 엔티티 fanout, 배치 처리 |
| Circuit Breaker | 5+ | maxFanout 초과 시 SKIP/ERROR 동작 |
| Deduplication | 3+ | 중복 요청 방지, 윈도우 설정 |
| 동시성 제어 | 5+ | Semaphore, 동시 요청 처리 |
| 에러 핸들링 | 5+ | 부분 실패, 타임아웃 |
| 입력 검증 | 5+ | 빈 값, 음수 버전, 특수문자 |
| Edge/Corner Case | 26+ | 대소문자, 유니코드, tombstone 제외 등 |
| 멀티 테넌트 | 3+ | 테넌트 격리 |

---

## 부록: 전체 시나리오 예제

```kotlin
// ===== 설정 =====
Ivm.configure {
    baseUrl = "http://localhost:8080"
    tenantId = "oliveyoung"
}

// ===== 쓰기 (Deploy) - 코드젠 Entities 사용 =====

// 시나리오 1: 상품 등록 (동기)
Ivm.client().ingest(Entities.Product) {
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.deploy()

// 시나리오 2: 검색은 즉시, 추천은 배치 (혼합 모드)
val ingested = Ivm.client().ingest().product {
    sku = "SKU-002"
    name = "비타민D"
    price = 20000
}.ingest()

val compiled = ingested.compile()
val mixedResult = compiled.ship {
    sync { opensearch { index("products") } }
    async { personalize { dataset("product-recs") } }
}

// 시나리오 3: 비동기 처리 + Job 대기
val job = Ivm.client().ingest(Entities.Product) {
    sku = "SKU-003"
    name = "비타민E"
    price = 25000
}.deployAsync()

// Job 완료 대기 (최대 5분, 1초 간격 폴링)
val result = Ivm.client().deploy.await(job.jobId)

// 시나리오 3-1: 단계별 비동기 체이닝
val ingested = Ivm.client().ingest().product { ... }.ingest()
val compileJob = ingested.compileAsync()  // 컴파일만 비동기
val compileAndShipJob = ingested.compileAndShipAsync()  // 컴파일 + Ship 비동기

// ===== 읽기 (Query) - 코드젠 Views 사용 =====

// 시나리오 4: 상품 조회 (타입 세이프)
val product: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
println("상품명: ${product.name}")
println("가격: ${product.price}원")

// 시나리오 5: 결과 처리
val product2: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()

println("상품명: ${product2.name}")
println("가격: ${product2.price}원")
println("재고: ${product2.stock}개")

// 시나리오 6: 범위 검색 + 페이지네이션 (타입 세이프)
val results = Ivm.client().query(Views.Product.Pdp)
    .tenant("oliveyoung")
    .range { 
        keyPrefix("SKU-")
        latestOnly()  // 최신 버전만
    }
    .limit(100)
    .list()

results.items.forEach { product: ProductPdpData ->
    println("${product.productId}: ${product.name}")
}

// 시나리오 7: 자동 페이지네이션 (타입 세이프)
Ivm.client().query(Views.Product.Pdp)
    .range { all() }
    .stream()
    .take(500)
    .forEach { product: ProductPdpData ->
        println("${product.productId}: ${product.name}")
    }

// 시나리오 8: 타입 세이프 조회
val typedProduct: ProductPdpData = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
println("${typedProduct.name}: ${typedProduct.price}원")

// 시나리오 9: Deploy Plan 설명 (Dry Run)
val plan = Ivm.client().ingest(Entities.Product) {
    sku = "SKU-004"
    name = "비타민F"
    price = 30000
}.explain()
println("활성화된 규칙: ${plan.activatedRules}")

// 시나리오 10: Plan API로 마지막 배포 계획 조회
val lastPlan = Ivm.client().plan.explainLastPlan("deploy-123")
println("의존성 그래프: ${lastPlan.graph}")
```

---

**문의**: SDK 관련 문의는 #ivm-sdk 채널로 연락주세요.
