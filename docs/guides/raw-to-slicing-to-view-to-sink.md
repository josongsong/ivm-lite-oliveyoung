# Raw → Slicing → View → Sink (인덱싱 파이프라인)

> **목적**: 인덱싱을 위한 데이터가 Raw부터 최종 Sink(검색 엔진)까지 전달되는 전체 과정 설명  
> **대상**: 검색 엔진 파이프라인 추가 예정

---

## 🎯 전체 개요

```
Raw Data (원본 데이터)
    ↓
Slicing (계약 기반 변환)
    ↓
Slice (물리 데이터, DynamoDB 저장)
    ↓
View (논리 뷰, Slice 병합)
    ↓
Sink (검색 엔진 인덱싱)
```

**핵심 개념**:
- **Raw**: 원본 비즈니스 데이터 (SSOT)
- **Slice**: 계약 기반으로 분리된 물리 데이터 단위
- **View**: 인덱싱을 위해 Slice들을 병합한 최종 문서 형태
- **Sink**: 검색 엔진(OpenSearch 등)으로 전달되는 최종 단계

---

## 📊 단계별 상세 설명

### 1단계: Raw Data (원본 데이터)

**위치**: PostgreSQL `raw_data` 테이블

**데이터 구조**:
```json
{
  "tenantId": "oliveyoung",
  "entityKey": "A000000001",
  "version": 1738000000000000001,
  "schemaId": "product.v1",
  "schemaVersion": "1.0.0",
  "content": {
    "prdtNo": "A000000001",
    "prdtName": "라운드랩 1025 독도 토너",
    "brand": { "code": "ROUNDLAB", "krName": "라운드랩" },
    "price": { "normal": 18000, "sale": 14400 },
    "images": [...],
    "categories": [...],
    ...
  }
}
```

**특징**:
- **SSOT (Single Source of Truth)**: 모든 데이터의 원본
- **비정규화된 구조**: 비즈니스 도메인 그대로 저장
- **버전 관리**: `version` 필드로 변경 이력 추적

---

### 2단계: Slicing (계약 기반 변환)

**위치**: `SlicingWorkflow.execute()` → `SlicingEngine.slice()`

**프로세스**:
1. **RuleSet Contract 로드**: `ruleset.core.v1.yaml` 등
2. **SliceDefinition 기반 변환**: 각 SliceType별로 데이터 추출
3. **Light JOIN 실행**: 필요시 관련 엔티티 조인
4. **Inverted Index 생성**: 검색용 인덱스 엔트리 생성
   - **정방향 인덱스**: 항상 생성 (검색용)
   - **역방향 인덱스**: `references` 있을 때만 생성 (Fanout용)

**RuleSet Contract Policy 구성**:

RuleSet Contract는 다음 policy 요소들로 구성됩니다:

#### 2.1 ImpactMap Policy (증분 최적화)

**목적**: 필드 변경 경로 → 영향받는 SliceType 매핑

**예시** (`ruleset-product-doc001.v1.yaml`):
```yaml
impactMap:
  CORE:
    - "/_meta/schemaVersion"
    - "/masterInfo/brand/*"
    - "/onlineInfo/prdtName"
    - "/onlineInfo/displayYn"
  PRICE:
    - "/options"
    - "/masterInfo/packaging/*"
  INVENTORY:
    - "/onlineInfo/orderQuantity/*"
    - "/onlineInfo/sellStatCode"
  MEDIA:
    - "/thumbnailImages"
    - "/videoInfo/*"
  CATEGORY:
    - "/displayCategories"
    - "/masterInfo/standardCategory/*"
  INDEX:
    - "/masterInfo/flags/*"
    - "/options"
    - "/displayCategories"
```

**동작 방식**:
- Raw Data 변경 시 `ChangeSet` 생성
- `ImpactCalculator`가 `impactMap`을 기반으로 영향받는 SliceType 계산
- 영향받은 SliceType만 재슬라이싱 (`slicePartial()`)
- **증분 최적화**: 변경되지 않은 SliceType은 재계산 안 함

#### 2.2 BuildRules Policy (필드 추출 규칙)

**목적**: 각 SliceType별로 어떤 필드를 추출할지 정의

**타입**:
- **PassThrough**: 필드 그대로 통과
- **MapFields**: 소스 필드를 타겟 필드로 매핑

**예시** (`ruleset-product-doc001.v1.yaml`):
```yaml
slices:
  - type: CORE
    buildRules:
      type: PassThrough
      fields:
        - "*"  # 모든 필드 통과
  
  - type: PRICE
    buildRules:
      type: PassThrough
      fields:
        - "options"
        - "masterInfo.packaging"
  
  - type: INDEX
    buildRules:
      type: PassThrough
      fields:
        - "*"  # 검색/필터용 파생 필드 생성
```

**동작 방식**:
- `SlicingEngine.buildSlice()`에서 `buildRules` 적용
- `PassThrough`: 지정된 필드만 추출
- `MapFields`: 필드명 변환 (예: `brandId` → `brand.code`)

#### 2.3 Join Policy (Light JOIN 규칙)

**목적**: 관련 엔티티 조인하여 Slice에 병합

**예시** (`ruleset.v1.yaml`):
```yaml
slices:
  - type: CORE
    buildRules:
      type: PassThrough
      fields: ["*"]
    joins:
      - name: brandInfo
        type: LOOKUP
        sourceFieldPath: brandId
        targetEntityType: BRAND
        targetKeyPattern: BRAND#{tenantId}#{value}
        required: false  # 브랜드 없어도 슬라이싱 진행
```

**동작 방식**:
- `JoinExecutor.executeJoins()` 실행
- `sourceFieldPath`에서 값 추출 → `targetEntityType` 조회
- 조인 결과를 원본 payload에 병합
- `required: false`면 조인 실패해도 슬라이싱 계속 진행

#### 2.4 Index Policy (인덱싱 규칙)

**목적**: Inverted Index 생성 및 Fanout 설정

**예시** (`ruleset-product-doc001.v1.yaml`):
```yaml
indexes:
  - type: brand
    selector: $.masterInfo.brand.code
    references: BRAND        # 역방향 인덱스 자동 생성
    maxFanout: 10000         # Fanout 임계값
  
  - type: category
    selector: $.displayCategories[*].sclsCtgrNo
    references: CATEGORY     # 역방향 인덱스 자동 생성
    maxFanout: 50000
  
  - type: keyword
    selector: $.additionalInfo.srchKeyWordText
    # references 없음 → 검색용 인덱스만 생성 (Fanout 없음)
```

**동작 방식**:
- `InvertedIndexBuilder.build()`가 `selector` 기반으로 인덱스 엔트리 생성
- `references` 있으면 역방향 인덱스도 자동 생성 (Fanout용)
- `maxFanout`: 역방향 인덱스 조회 시 circuit breaker 임계값
- 검색 엔진 인덱싱과는 별개 (내부 조회용)

**인덱스 생성 상세 과정**:

**1. 정방향 인덱스 (검색용) - 항상 생성**

**목적**: 특정 값으로 엔티티 검색 (예: "라운드랩" 브랜드의 모든 상품 찾기)

**생성 예시**:
```kotlin
// Slice 데이터에서 selector로 값 추출
selector: $.masterInfo.brand.code
→ 값: "ROUNDLAB"

// 정방향 인덱스 생성
InvertedIndexEntry(
    indexType: "brand",
    indexValue: "roundlab",  // lowercase 정규화
    refEntityKey: "PRODUCT#oliveyoung#A000000001",  // 현재 엔티티
    targetEntityKey: "PRODUCT#oliveyoung#A000000001",
    ...
)
```

**조회 예시**:
```kotlin
// "라운드랩" 브랜드의 모든 상품 찾기
invertedIndexRepo.listTargets(
    tenantId = "oliveyoung",
    indexType = "brand",
    indexValue = "roundlab"
)
// → [PRODUCT#oliveyoung#A000000001, PRODUCT#oliveyoung#A000000002, ...]
```

**2. 역방향 인덱스 (Fanout용) - references 있을 때만 생성**

**목적**: 참조 엔티티 변경 시 영향받는 엔티티 찾기 (예: BRAND 변경 → 연관 PRODUCT 재슬라이싱)

**생성 예시**:
```kotlin
// selector에서 추출한 값이 EntityKey 형식이거나 entityId만
selector: $.masterInfo.brand.code
→ 값: "ROUNDLAB" (entityId만)

// 역방향 인덱스 생성
InvertedIndexEntry(
    indexType: "product_by_brand",  // {entityType}_by_{references}
    indexValue: "roundlab",  // entityId만 저장 (lowercase)
    refEntityKey: "BRAND#oliveyoung#roundlab",  // 참조되는 엔티티
    targetEntityKey: "PRODUCT#oliveyoung#A000000001",  // 참조하는 엔티티 (재슬라이싱 대상)
    ...
)
```

**Fanout 실행 흐름**:

**시나리오**: BRAND 엔티티 변경 → 연관된 모든 PRODUCT 재슬라이싱

```
1. BRAND 엔티티 변경
   BRAND#oliveyoung#roundlab (version: 100 → 101)
   
2. FanoutWorkflow 실행
   - 역방향 인덱스 조회: indexType="product_by_brand", indexValue="roundlab"
   - 영향받는 PRODUCT 찾기: [PRODUCT#oliveyoung#A000000001, ...]
   
3. 각 PRODUCT 재슬라이싱
   - SlicingWorkflow.execute() 호출
   - CORE Slice 재생성 (brand 정보 업데이트)
   - 새로운 인덱스 생성
   
4. 결과
   - 모든 연관 PRODUCT가 최신 BRAND 정보 반영
```

**실제 RuleSet Contract 사례**:

**사례 1: Brand 인덱스 (Fanout 활성화)**
```yaml
indexes:
  - type: brand
    selector: $.masterInfo.brand.code
    references: BRAND        # ← Fanout 활성화
    maxFanout: 10000         # ← 최대 10,000개까지 Fanout 허용
```

**생성되는 인덱스**:
- **정방향**: `indexType="brand"`, `indexValue="roundlab"` → PRODUCT 검색용
- **역방향**: `indexType="product_by_brand"`, `indexValue="roundlab"` → Fanout용

**Fanout 동작**:
- BRAND 변경 시 → `product_by_brand` 인덱스로 영향받는 PRODUCT 찾기
- 최대 10,000개까지 허용 (초과 시 circuit breaker 트립)

**사례 2: Category 인덱스 (Fanout 활성화, 높은 임계값)**
```yaml
indexes:
  - type: category
    selector: $.displayCategories[*].sclsCtgrNo
    references: CATEGORY
    maxFanout: 50000         # ← 카테고리는 연관 상품이 많을 수 있음
```

**생성되는 인덱스**:
- **정방향**: `indexType="category"`, `indexValue="cat001"` → PRODUCT 검색용
- **역방향**: `indexType="product_by_category"`, `indexValue="cat001"` → Fanout용

**배열 처리**:
- `[*]` 패턴으로 배열의 모든 요소에 대해 인덱스 생성
- 예: `displayCategories: [cat001, cat002]` → 각각에 대해 인덱스 생성

**사례 3: Keyword 인덱스 (Fanout 비활성화)**
```yaml
indexes:
  - type: keyword
    selector: $.additionalInfo.srchKeyWordText
    # references 없음 → 검색용 인덱스만 생성
```

**생성되는 인덱스**:
- **정방향**: `indexType="keyword"`, `indexValue="토너"` → PRODUCT 검색용
- **역방향**: 생성 안 됨 (Fanout 불필요)

**사례 4: GTIN 인덱스 (고유 식별자)**
```yaml
indexes:
  - type: gtin
    selector: $.masterInfo.gtin
    # references 없음 → 검색용 인덱스만 생성
```

**생성되는 인덱스**:
- **정방향**: `indexType="gtin"`, `indexValue="8801234567890"` → PRODUCT 검색용
- **역방향**: 생성 안 됨 (GTIN은 다른 엔티티를 참조하지 않음)

**Fanout Circuit Breaker**:

**동작 방식**:
```kotlin
// FanoutWorkflow에서 영향받는 엔티티 수 조회
val count = invertedIndexRepo.countByIndexType(
    tenantId = tenantId,
    indexType = "product_by_brand",
    indexValue = "roundlab"
)
// → 예: 15,000개

// Circuit Breaker 체크
if (count > maxFanout) {  // 15,000 > 10,000
    // Fanout 중단 (안전장치)
    return Result.Err(CircuitBreakerTrippedError(...))
}
```

**임계값 설정 가이드**:
- **낮은 임계값 (1,000~10,000)**: 일대다 관계가 적은 경우 (예: Brand → Product)
- **높은 임계값 (10,000~50,000)**: 일대다 관계가 많은 경우 (예: Category → Product)
- **무제한 (null)**: Fanout 비활성화 또는 매우 큰 규모 허용

**생성되는 Slice 타입** (예시):
- `CORE`: 식별/명칭/브랜드/상태 (ImpactMap: `/masterInfo/brand/*`, `/onlineInfo/prdtName` 등)
- `PRICE`: 가격/할인/마진 (ImpactMap: `/options`, `/masterInfo/packaging/*`)
- `INVENTORY`: 재고/주문 제한 (ImpactMap: `/onlineInfo/orderQuantity/*`, `/onlineInfo/sellStatCode`)
- `MEDIA`: 이미지/비디오 (ImpactMap: `/thumbnailImages`, `/videoInfo/*`)
- `CATEGORY`: 카테고리 정보 (ImpactMap: `/displayCategories`, `/masterInfo/standardCategory/*`)
- `INDEX`: 검색/필터용 파생 필드 (ImpactMap: `/masterInfo/flags/*`, `/options` 등)

**결과물**: `SliceRecord[]` (DynamoDB `ivm-lite-data` 테이블 저장)

**예시 Slice 구조**:
```json
// CORE Slice
{
  "sliceType": "CORE",
  "data": {
    "prdtNo": "A000000001",
    "prdtName": "라운드랩 1025 독도 토너",
    "brand": { "code": "ROUNDLAB", "krName": "라운드랩" },
    "displayYn": true,
    "sellStatCode": "ON_SALE"
  }
}

// PRICE Slice
{
  "sliceType": "PRICE",
  "data": {
    "normalPrice": 18000,
    "salePrice": 14400,
    "discountRate": 20,
    "marginRate": 15.5
  }
}
```

**특징**:
- **물리 분리**: 각 SliceType별로 독립 저장
- **증분 최적화**: `impactMap` 기반으로 변경된 SliceType만 재계산
- **결정성 보장**: 동일 입력 → 동일 Slice hash
- **Policy 기반**: RuleSet Contract의 `buildRules`, `joins`, `indexes` 정책 적용

---

### 3단계: View (논리 뷰, Slice 조회 및 병합)

**위치**: 
- **조회용**: `QueryViewWorkflow.execute()` → ViewDefinition Contract 기반 조회
- **인덱싱용**: `ShipWorkflow.mergeSlices()` → 최종 인덱싱 문서 생성

**ViewDefinition Contract 기반 조회** (`QueryViewWorkflow`):

**프로세스**:
1. **ViewDefinition Contract 로드**: `view.product.detail.v1.yaml` 등
2. **RuleSet 참조 확인**: `ruleSetRef`를 통해 어떤 RuleSet으로 생성된 Slice인지 확인
3. **필요한 SliceType 결정**: `requiredSlices` + `optionalSlices`
4. **Slice 조회**: `sliceRepository.getByVersion()` → 해당 버전의 모든 Slice 조회
5. **MissingPolicy 적용**: 필수 Slice 누락 시 정책에 따라 처리
6. **View 데이터 생성**: `buildViewData()` → Slice들을 하나의 JSON 문서로 병합

**ViewDefinition Contract 예시** (`view-product-detail.v1.yaml`):
```yaml
kind: VIEW_DEFINITION
id: view.product.detail.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_DETAIL
entityType: PRODUCT
description: "상품 상세 - 전체 정보 조합"

requiredSlices:
  - CORE
  - PRICE
  - MEDIA

optionalSlices:
  - INVENTORY
  - CATEGORY
  - INDEX

missingPolicy: FAIL_CLOSED  # 필수 Slice 누락 시 실패

partialPolicy:
  allowed: true
  optionalOnly: true  # optional Slice만 누락 허용
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: true

fallbackPolicy: NONE

ruleSetRef:  # ← RuleSet Contract 참조
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

**QueryViewWorkflow 실행 흐름**:
```kotlin
// 1. ViewDefinition Contract 로드
val viewDef = contractRegistry.loadViewDefinitionContract(
    ContractRef("view.product.detail.v1", "1.0.0")
)

// 2. 필요한 SliceType 결정
val allSliceTypes = (viewDef.requiredSlices + viewDef.optionalSlices)
    .distinct()
    .sortedBy { it.name }
// → [CORE, PRICE, MEDIA, INVENTORY, CATEGORY, INDEX]

// 3. Slice 조회 (이미 JOIN된 데이터 포함)
val allSlices = sliceRepo.getByVersion(tenantId, entityKey, version)
val slices = allSlices.filter { it.sliceType in allSliceTypes }

// 4. MissingPolicy 적용
val gotTypes = slices.map { it.sliceType }.toSet()
val missingRequired = viewDef.requiredSlices.filter { it !in gotTypes }

when (viewDef.missingPolicy) {
    MissingPolicy.FAIL_CLOSED -> {
        if (missingRequired.isNotEmpty()) {
            return Result.Err(MissingSliceError(...))
        }
    }
    MissingPolicy.PARTIAL_ALLOWED -> {
        // partialPolicy에 따라 처리
    }
}

// 5. View 데이터 생성 (Slice 병합)
val viewData = buildViewData(viewId, entityKey, version, slices, ...)
```

**인덱싱용 View 병합** (`ShipWorkflow` → `SliceMerger`):

**프로세스**:
1. **Slice 조회**: `sliceRepository.getByVersion()` → 모든 SliceType 조회
2. **병합**: `SliceMerger.merge()` → Slice들을 하나의 JSON 문서로 병합
3. **Tombstone 필터링**: 삭제된 Slice는 제외

**병합 로직** (`SliceMerger.merge()`):
```kotlin
// pkg/slices/domain/SliceMerger.kt
object SliceMerger {
    fun merge(slices: List<SliceRecord>, excludeTombstones: Boolean = true): Result<String> {
        return try {
            val merged = buildJsonObject {
                slices.forEach { slice ->
                    if (!excludeTombstones || slice.tombstone == null) {
                        val sliceJson = json.parseToJsonElement(slice.data)
                        if (sliceJson is JsonObject) {
                            sliceJson.forEach { (key, value) ->
                                put(key, value)  // 모든 필드를 하나의 JSON으로 병합
                            }
                        }
                    }
                }
            }
            Result.Ok(json.encodeToString(JsonObject.serializer(), merged))
        } catch (e: Exception) {
            Result.Err(DomainError.InvariantViolation("SliceMerger: JSON parsing failed"))
        }
    }
}

// ShipWorkflow.kt에서 사용:
val mergedPayload = when (val mergeResult = SliceMerger.merge(slices)) {
    is Result.Ok -> mergeResult.value
    is Result.Err -> return Result.Err(mergeResult.error)
}
```

**최종 View 문서 구조** (인덱싱용):
```json
{
  // CORE Slice에서
  "prdtNo": "A000000001",
  "prdtName": "라운드랩 1025 독도 토너",
  "brand": { "code": "ROUNDLAB", "krName": "라운드랩" },
  "displayYn": true,
  "sellStatCode": "ON_SALE",
  
  // PRICE Slice에서
  "normalPrice": 18000,
  "salePrice": 14400,
  "discountRate": 20,
  "marginRate": 15.5,
  
  // MEDIA Slice에서
  "images": [...],
  "videos": [...],
  
  // CATEGORY Slice에서
  "categories": [...],
  
  // INDEX Slice에서
  "searchKeywords": [...],
  "filterBuckets": {...}
}
```

**특징**:
- **논리 뷰**: 물리적으로는 분리되어 있으나, 인덱싱 시 하나로 병합
- **완전한 문서**: 검색 엔진에 인덱싱하기 위한 최종 형태
- **동적 병합**: SliceType이 추가/변경되어도 자동 반영

---

### 4단계: Sink (검색 엔진 인덱싱)

**위치**: `ShipWorkflow.execute()` → `SinkPort.ship()` → `OpenSearchSinkAdapter.ship()`

**프로세스**:
1. **Sink 선택**: `SinkRule` 기반 또는 명시적 지정
2. **View 문서 전달**: 병합된 JSON 문서를 Sink로 전송
3. **인덱싱**: 검색 엔진에 문서 인덱싱

**OpenSearch 예시** (`OpenSearchSinkAdapter.ship()`):
```kotlin
override suspend fun ship(
    tenantId: TenantId,
    entityKey: EntityKey,
    version: Long,
    payload: String  // ← 병합된 View 문서
): SinkPort.Result<ShipResult> {
    val documentId = buildDocumentId(tenantId, entityKey)
    val indexName = buildIndexName(tenantId)  // 예: "oliveyoung-products"
    
    val response = client.put("${config.endpoint}/$indexName/_doc/$documentId") {
        contentType(ContentType.Application.Json)
        setBody(payload)  // ← View 문서를 OpenSearch에 인덱싱
    }
    
    return SinkPort.Result.Ok(ShipResult(...))
}
```

**인덱싱 결과**:
- **OpenSearch 인덱스**: `oliveyoung-products`
- **문서 ID**: `oliveyoung:A000000001`
- **문서 내용**: 병합된 View JSON

**특징**:
- **멱등성**: 동일 `(tenantId, entityKey, version)` → 동일 문서
- **비동기 처리**: Outbox를 통한 안정적 전달
- **다중 Sink 지원**: OpenSearch, Personalize 등 동시 전송 가능

---

## 🔄 전체 데이터 흐름도

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Raw Data (PostgreSQL)                                     │
│    - tenantId: "oliveyoung"                                 │
│    - entityKey: "A000000001"                                 │
│    - version: 1738000000000000001                            │
│    - content: { 원본 비즈니스 데이터 }                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ SlicingWorkflow.execute()
┌─────────────────────────────────────────────────────────────┐
│ 2. Slicing (SlicingEngine)                                  │
│    - RuleSet Contract 로드                                   │
│    - SliceDefinition 기반 변환                               │
│    - Light JOIN 실행                                         │
│    - Inverted Index 생성                                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ Slice 저장 (DynamoDB)
┌─────────────────────────────────────────────────────────────┐
│ 3. Slice (DynamoDB)                                         │
│    - CORE Slice: { prdtNo, prdtName, brand, ... }           │
│    - PRICE Slice: { normalPrice, salePrice, ... }          │
│    - MEDIA Slice: { images, videos, ... }                   │
│    - CATEGORY Slice: { categories, ... }                    │
│    - INDEX Slice: { searchKeywords, filterBuckets, ... }    │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ ShipWorkflow.execute()
┌─────────────────────────────────────────────────────────────┐
│ 4. View (논리 병합)                                         │
│    - sliceRepository.getByVersion() → 모든 Slice 조회       │
│    - mergeSlices() → 하나의 JSON 문서로 병합                │
│    - Tombstone 필터링                                        │
│    - 최종 문서: { 모든 Slice 필드 병합 }                     │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼ SinkPort.ship()
┌─────────────────────────────────────────────────────────────┐
│ 5. Sink (검색 엔진)                                          │
│    - OpenSearch: PUT /oliveyoung-products/_doc/...          │
│    - Personalize: PUT /items/...                            │
│    - 기타 검색 엔진: 각각의 API 호출                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 핵심 설계 원칙

### 1. 물리 분리, 논리 병합

- **물리**: Slice는 DynamoDB에 분리 저장 (증분 최적화)
- **논리**: 인덱싱 시 View로 병합 (완전한 문서)

### 2. Policy 기반 변환

**RuleSet Contract Policy** (Slicing 단계):
- **ImpactMap Policy**: 필드 변경 → 영향받는 SliceType 매핑
- **BuildRules Policy**: SliceType별 필드 추출 규칙
- **Join Policy**: 관련 엔티티 조인 규칙 (Slicing 시 실행)
- **Index Policy**: Inverted Index 생성 및 Fanout 설정

**ViewDefinition Contract Policy** (조회 단계):
- **RequiredSlices Policy**: 필수 SliceType 정의
- **OptionalSlices Policy**: 선택적 SliceType 정의
- **MissingPolicy**: 필수 Slice 누락 시 정책 (FAIL_CLOSED, PARTIAL_ALLOWED)
- **RuleSetRef Policy**: 참조하는 RuleSet Contract 명시

### 2. 계약 기반 변환

- **RuleSet Contract**: Raw → Slice 변환 규칙 정의
  - **ImpactMap Policy**: 필드 변경 경로 → 영향받는 SliceType 매핑
  - **BuildRules Policy**: SliceType별 필드 추출 규칙 (PassThrough, MapFields)
  - **Join Policy**: 관련 엔티티 조인 규칙 (LOOKUP) - **Slicing 단계에서 실행**
  - **Index Policy**: Inverted Index 생성 및 Fanout 설정
- **ViewDefinition Contract**: Slice → View 조회 규칙 정의
  - **RequiredSlices Policy**: 필수 SliceType 정의
  - **OptionalSlices Policy**: 선택적 SliceType 정의
  - **MissingPolicy**: 필수 Slice 누락 시 정책
  - **RuleSetRef Policy**: 참조하는 RuleSet Contract (JOIN Policy 확인용)
- **SinkRule Contract**: Slice → Sink 라우팅 규칙 정의

### 3. 멱등성 보장

- **Raw**: `(tenantId, entityKey, version)` → 고유
- **Slice**: `(tenantId, entityKey, version, sliceType)` → 고유
- **View**: `(tenantId, entityKey, version)` → 고유
- **Sink**: 동일 입력 → 동일 인덱싱 결과

### 4. 비동기 처리

- **Outbox Pattern**: Ship은 Outbox를 통해 비동기 처리
- **자동 트리거**: Slicing 완료 시 SinkRule 기반 자동 ShipRequested 생성
- **재시도**: 실패 시 자동 재시도 (최대 5회)

---

## 🔌 추가 검색 엔진 파이프라인 추가 방법

### 1. SinkRule Contract 정의

**예시**: `sink-rule-elasticsearch.v1.yaml`
```yaml
id: sink-rule-elasticsearch.v1
version: "1.0.0"
status: ACTIVE
input:
  entityType: "product"
  sliceTypes:
    - CORE
    - PRICE
    - MEDIA
    - CATEGORY
    - INDEX
target:
  sinkType: "elasticsearch"
  sinkId: "elasticsearch-prod"
docId:
  format: "{tenantId}:{entityKey}"
commit:
  mode: ASYNC
```

### 2. SinkAdapter 구현

**예시**: `ElasticsearchSinkAdapter.kt`
```kotlin
class ElasticsearchSinkAdapter(
    private val config: ElasticsearchConfig,
    private val client: HttpClient
) : SinkPort {
    override suspend fun ship(
        tenantId: TenantId,
        entityKey: EntityKey,
        version: Long,
        payload: String  // ← 병합된 View 문서
    ): SinkPort.Result<ShipResult> {
        val documentId = "${tenantId.value}:${entityKey.value}"
        val indexName = "${tenantId.value}-products"
        
        val response = client.put("${config.endpoint}/$indexName/_doc/$documentId") {
            contentType(ContentType.Application.Json)
            setBody(payload)  // ← View 문서 전달
        }
        
        return SinkPort.Result.Ok(ShipResult(...))
    }
}
```

### 3. SinkAdapter 등록

**위치**: `AdapterModule.kt`
```kotlin
single<SinkPort>(qualifier = named("elasticsearch")) {
    ElasticsearchSinkAdapter(
        config = get<AppConfig>().sinks.elasticsearch,
        client = get()
    )
}
```

### 4. SinkRule 등록

**위치**: `SinkRuleRegistry` (InMemory 또는 DynamoDB)
```kotlin
sinkRuleRegistry.register(
    SinkRule(
        id = "sink-rule-elasticsearch.v1",
        status = SinkRuleStatus.ACTIVE,
        input = SinkRuleInput(
            entityType = "product",
            sliceTypes = listOf(SliceType.CORE, SliceType.PRICE, ...)
        ),
        target = SinkRuleTarget(
            sinkType = "elasticsearch",
            sinkId = "elasticsearch-prod"
        ),
        ...
    )
)
```

### 5. 자동 전송

**자동 동작**:
1. `SlicingWorkflow` 완료
2. `OutboxPollingWorker`가 `SlicingCompleted` 이벤트 처리
3. 매칭되는 SinkRule 기반으로 `ShipRequested` outbox 생성
4. `ShipEventHandler`가 `ShipWorkflow.execute()` 호출
5. `ShipWorkflow`가 `SliceMerger.merge()` → View 문서 생성
6. `ElasticsearchSinkAdapter.ship()` 호출 → 인덱싱

**결과**: 새로운 검색 엔진이 자동으로 데이터를 수신!

---

## 📋 요약

| 단계 | 데이터 형태 | 저장 위치 | 목적 |
|------|------------|----------|------|
| **Raw** | 원본 비즈니스 데이터 | PostgreSQL | SSOT, 버전 관리 |
| **Slice** | 계약 기반 분리 데이터 | DynamoDB | 증분 최적화, 물리 분리 |
| **View** | Slice 병합 문서 | 메모리 (임시) | 인덱싱용 완전한 문서 |
| **Sink** | 인덱싱된 문서 | 검색 엔진 | 검색/추천 서비스 |

**핵심 메시지**:
- **Raw → Slice**: RuleSet Contract Policy 기반 변환 (물리 분리)
  - ImpactMap Policy: 증분 최적화
  - BuildRules Policy: 필드 추출
  - **Join Policy: 엔티티 조인 (Slicing 단계에서 실행, 조회된 Slice에 이미 포함됨)**
  - **Index Policy: 인덱스 생성**
    - **정방향 인덱스**: 항상 생성 (검색용, 예: brand="roundlab" → PRODUCT 찾기)
    - **역방향 인덱스**: `references` 있을 때만 생성 (Fanout용, 예: product_by_brand="roundlab" → BRAND 변경 시 영향받는 PRODUCT 찾기)
    - **Fanout**: 참조 엔티티 변경 시 역방향 인덱스로 영향받는 엔티티 찾아 재슬라이싱
- **Slice → View**: ViewDefinition Contract Policy 기반 조회 및 병합
  - RequiredSlices Policy: 필수 SliceType 결정
  - MissingPolicy: 누락 처리 정책
  - RuleSetRef Policy: 참조하는 RuleSet 확인 (JOIN Policy 확인용)
  - **조회 시 추가 JOIN 없음** (이미 JOIN된 Slice 사용)
- **View → Sink**: 검색 엔진 전달 (멱등성 보장)

**확장성**:
- 새로운 검색 엔진 추가 시 `SinkAdapter` 구현 + `SinkRule` 등록만 하면 자동 전송!
- RuleSet Contract Policy 변경 시 자동으로 새로운 Slice 구조 반영

---

## 📋 RuleSet Contract Policy 참고

### 실제 계약 파일 예시

**파일**: `src/main/resources/contracts/v1/ruleset-product-doc001.v1.yaml`

```yaml
kind: RULESET
id: ruleset.product.doc001.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

# ImpactMap Policy: 필드 변경 경로 → 영향받는 SliceType
impactMap:
  CORE:
    - "/_meta/schemaVersion"
    - "/masterInfo/brand/*"
    - "/onlineInfo/prdtName"
    - "/onlineInfo/displayYn"
  PRICE:
    - "/options"
    - "/masterInfo/packaging/*"
  # ... 기타 SliceType

# BuildRules Policy: SliceType별 필드 추출 규칙
slices:
  - type: CORE
    buildRules:
      type: PassThrough
      fields: ["*"]
    joins: []
  
  - type: PRICE
    buildRules:
      type: PassThrough
      fields:
        - "options"
        - "masterInfo.packaging"
  
  - type: INDEX
    buildRules:
      type: PassThrough
      fields: ["*"]

# Index Policy: Inverted Index 생성 및 Fanout 설정
indexes:
  - type: brand
    selector: $.masterInfo.brand.code
    references: BRAND        # 역방향 인덱스 자동 생성
    maxFanout: 10000         # Fanout 임계값
  
  - type: category
    selector: $.displayCategories[*].sclsCtgrNo
    references: CATEGORY
    maxFanout: 50000
```

**Policy 적용 순서**:
1. `impactMap` → 영향받는 SliceType 계산 (증분 최적화)
2. `buildRules` → 필드 추출 (PassThrough 또는 MapFields)
3. `joins` → 관련 엔티티 조인 (LOOKUP)
4. `indexes` → Inverted Index 생성 (검색/Fanout용)
