# DOC-001 상품 마스터 데이터 Slicing & View 정책 제안

**작성일**: 2026-01-16  
**기반 문서**: DOC-001 Flat Catalog v3.0  
**참고 RFC**: RFC-IR-031 (rfc011-doc001-flat-catalog.md)

---

## 목차

1. [Slicing 전략](#1-slicing-전략)
2. [SliceType 매핑](#2-slicetype-매핑)
3. [ImpactMap 상세 정의](#3-impactmap-상세-정의)
4. [ViewDefinition 정책](#4-viewdefinition-정책)
5. [계약 파일 예시](#5-계약-파일-예시)
6. [View 정책 요약표](#6-view-정책-요약표)
7. [추가 고려사항](#7-추가-고려사항)
8. [다음 단계](#8-다음-단계)

---

## 1. Slicing 전략

### 1.1 설계 원칙

- **최소 물리 Slice**: 조회/조인/증분에 최적화된 소수의 안정적 SliceType만 물리화
- **증분 최적화**: 필드 변경 시 최소한의 Slice만 재계산되도록 ImpactMap 설계
- **Ship 단계 변환**: 타겟별 문서(OpenSearch/Personalize)는 Ship 단계에서 생성
- **결정성 보장**: 동일 입력 → 동일 Slice hash

### 1.2 물리 SliceType (6개)

| SliceType | 목적 | 주요 섹션 |
|-----------|------|-----------|
| **CORE** | 식별/명칭/브랜드/상태/플래그 | Meta, Audit, Master Info (기본식별, 브랜드, 플래그, 상태), Online Info (기본) |
| **PRICE** | 가격/할인/마진 | Options (가격 정보), Master Info (포장/규격) |
| **INVENTORY** | 판매 가능/주문 제한 | Online Info (주문수량제한), Reservation Sale, Master Info (유통기한) |
| **MEDIA** | 썸네일/비디오 | Thumbnail Images, Video Info |
| **CATEGORY** | 노출 카테고리/표준 카테고리 | Display Categories, Master Info (카테고리) |
| **INDEX** | 검색/필터용 파생 필드 | 모든 섹션에서 파생 (버킷/정규화 상태/검색 필터/키워드) |

> **Note**: 기존 `DERIVED` → `INDEX`로 변경. 목적이 "검색/필터 인덱싱"임을 명확히 함.

---

## 2. SliceType 매핑

### 2.1 CORE Slice

**목적**: 조회 공통 필수 정보

**포함 섹션**:
- `_meta` (schemaVersion, savedAt)
- `_audit` (createdBy, createdAt, updatedBy, updatedAt)
- `masterInfo`:
  - 기본 식별 정보 (gtin, gdsCd, gdsNm, gdsEngNm)
  - 브랜드 정보 (brand.code, brand.krName, brand.enName)
  - 플래그 (flags.*)
  - 상품 상태/기간 (gdsStatNm, buyTypNm, gdsRegYmd 등)
  - 담당자 정보 (md, scm)
- `onlineInfo`:
  - 상품 식별 정보 (prdtNo, prdtName, onlinePrdtName)
  - 상품 상태 정보 (prdtStatCode, sellStatCode, displayYn, prdtGbnCode)
  - 온라인 담당자/브랜드 (onlineMd, onlineBrand)
  - 앱 제외 여부 (appExcluPrdtYn)
- `emblemInfo` (cleanBeautyYn, crueltyFreeYn 등)

**조인**:
- Brand Join: `masterInfo.brand.code` → BRAND 엔티티

**Impact 경로**:
```yaml
CORE:
  - "/_meta/schemaVersion"
  - "/_meta/savedAt"
  - "/_audit/*"
  - "/masterInfo/gtin"
  - "/masterInfo/gdsCd"
  - "/masterInfo/gdsNm"
  - "/masterInfo/gdsEngNm"
  - "/masterInfo/brand/*"
  - "/masterInfo/flags/*"
  - "/masterInfo/gdsStatNm"
  - "/masterInfo/buyTypNm"
  - "/masterInfo/gdsRegYmd"
  - "/masterInfo/md/*"
  - "/masterInfo/scm/*"
  - "/onlineInfo/prdtNo"
  - "/onlineInfo/prdtName"
  - "/onlineInfo/onlinePrdtName"
  - "/onlineInfo/prdtStatCode"
  - "/onlineInfo/sellStatCode"
  - "/onlineInfo/displayYn"
  - "/onlineInfo/prdtGbnCode"
  - "/onlineInfo/onlineMd/*"
  - "/onlineInfo/onlineBrand/*"
  - "/onlineInfo/appExcluPrdtYn"
  - "/emblemInfo/*"
```

### 2.2 PRICE Slice

**목적**: 가격/할인/마진 정보 (대표 SKU + 요약)

**포함 섹션**:
- `options[]` (전체 옵션 가격 정보)
  - 대표 SKU: `rprstYn == 1`인 옵션
  - 요약: min/max 가격, 할인율 범위
- `masterInfo.packaging` (케이스/박스 수량 - 가격 계산용)

**파생 필드** (IR Builder에서 생성):
- `pricing.primary.normal`: 대표 SKU 정상가
- `pricing.primary.sale`: 대표 SKU 판매가
- `pricing.primary.discountRate`: 대표 SKU 할인율
- `pricing.min`: 최소 가격
- `pricing.max`: 최대 가격
- `pricing.isOnSale`: 할인 여부

**Impact 경로**:
```yaml
PRICE:
  - "/options"
  - "/masterInfo/packaging/*"
```

### 2.3 INVENTORY Slice

**목적**: 판매 가능/주문 제한/재고 관련

**포함 섹션**:
- `onlineInfo.orderQuantity` (min, max, increaseUnit)
- `onlineInfo.orderLimits` (brandMin, brandMax, classMin, classMax)
- `reservationSale` (rsvCheckYn, restrictionPeriod, restrictShipmentYn, expectedInbound)
- `masterInfo`:
  - 유통기한 정보 (shelfLifeManageYn, shelfLifeAvailableDays 등)
  - 정리/폐기/반품 정보 (clearanceYn, disposalAllowed, returnAllowed)

**파생 필드**:
- `availability.isSellable`: 판매 가능 여부 (sellStatCode + displayYn + 기타 조건)
- `availability.sellStatus`: 판매 상태
- `availability.orderQuantity`: 주문 수량 제한
- `availability.orderLimits`: 브랜드/분류별 제한

**Impact 경로**:
```yaml
INVENTORY:
  - "/onlineInfo/orderQuantity/*"
  - "/onlineInfo/orderLimits/*"
  - "/onlineInfo/sellStatCode"
  - "/reservationSale/*"
  - "/masterInfo/shelfLifeManageYn"
  - "/masterInfo/shelfLifeAvailableDays"
  - "/masterInfo/shelfLifeInboundAvailableDays"
  - "/masterInfo/shelfLifeOutboundAvailableDays"
  - "/masterInfo/clearanceYn"
  - "/masterInfo/disposalAllowed"
  - "/masterInfo/returnAllowed"
```

### 2.4 MEDIA Slice

**목적**: 썸네일/비디오 요약

**포함 섹션**:
- `thumbnailImages[]` (전체 썸네일)
- `videoInfo` (exposureType, entries[])

**파생 필드**:
- `media.thumbnails`: 정렬된 썸네일 배열 (typeCode, seq 기준)
- `media.videos`: 비디오 ID 배열
- `media.mainThumbnail`: 대표 썸네일 URL

**Impact 경로**:
```yaml
MEDIA:
  - "/thumbnailImages"
  - "/videoInfo/*"
```

### 2.5 CATEGORY Slice

**목적**: 노출 카테고리/표준 카테고리 요약

**포함 섹션**:
- `displayCategories[]` (노출 카테고리 - 우선)
- `masterInfo.standardCategory` (표준 카테고리 - fallback)

**조인**:
- Category Join: `displayCategories[0].sclsCtgrNo` → CATEGORY 엔티티 (fallback: `masterInfo.standardCategory.small.code`)

**파생 필드**:
- `category.display.categoryId`: 대표 노출 카테고리 ID
- `category.display.path`: 카테고리 경로
- `category.standard`: 표준 카테고리 정보

**Impact 경로**:
```yaml
CATEGORY:
  - "/displayCategories"
  - "/masterInfo/standardCategory/*"
```

### 2.6 INDEX Slice

**목적**: 검색/필터용 파생 필드 (검색 인덱싱, 필터 facet, 정렬 기준)

**포함 섹션**: 모든 섹션에서 파생 계산

**파생 필드** (IR Builder에서 계산):
- `index.priceBucket`: 가격 버킷 (예: "0-10000", "10000-30000", ...)
- `index.isOnSale`: 할인 여부
- `index.isSearchable`: 검색 가능 여부 (displayYn + sellStatCode + languageDisplayList)
- `index.isDisplayable`: 전시 가능 여부
- `index.badges`: 배지 목록 (cleanBeautyYn, crueltyFreeYn 등)
- `index.keywords`: 검색 키워드 (srchKeyWordText + 자동 추출)
- `index.localizedNames`: 다국어 상품명 (languageDisplayList 기반)
- `index.isSearchableByLang`: 언어별 검색 가능 여부
- `index.sortScore`: 정렬 점수 (판매량, 리뷰, 최신순 등)

**Impact 경로**: 모든 필드 변경이 INDEX에 영향
```yaml
INDEX:
  - "/masterInfo/flags/*"
  - "/options"
  - "/displayCategories"
  - "/onlineInfo/*"
  - "/languageDisplayList"
  - "/emblemInfo/*"
  - "/additionalInfo/srchKeyWordText"
```

---

## 3. ImpactMap 상세 정의

### 3.1 전체 ImpactMap

```yaml
impactMap:
  CORE:
    # Meta/Audit
    - "/_meta/schemaVersion"
    - "/_meta/savedAt"
    - "/_audit/*"
    # Master Info - 기본 식별
    - "/masterInfo/gtin"
    - "/masterInfo/manufacturerGtin"
    - "/masterInfo/gdsCd"
    - "/masterInfo/gaCode"
    - "/masterInfo/gdsNm"
    - "/masterInfo/gdsEngNm"
    # Master Info - 브랜드
    - "/masterInfo/brand/*"
    # Master Info - 플래그
    - "/masterInfo/flags/*"
    # Master Info - 상태/기간
    - "/masterInfo/onyoneSpNm"
    - "/masterInfo/buyTypNm"
    - "/masterInfo/gdsStatNm"
    - "/masterInfo/manBabySpNm"
    - "/masterInfo/gdsRegYmd"
    - "/masterInfo/validPrdDdNum"
    - "/masterInfo/poutTlmtDdNum"
    - "/masterInfo/infnSelImpsYnValue"
    - "/masterInfo/salesEndPlannedDate"
    - "/masterInfo/salesEndDate"
    - "/masterInfo/salesEndReason"
    # Master Info - 담당자
    - "/masterInfo/md/*"
    - "/masterInfo/scm/*"
    # Online Info - 식별
    - "/onlineInfo/prdtNo"
    - "/onlineInfo/agoodsNo"
    - "/onlineInfo/aGoodsNm"
    - "/onlineInfo/prdtName"
    - "/onlineInfo/onlinePrdtName"
    - "/onlineInfo/prdtSbttlName"
    # Online Info - 상태
    - "/onlineInfo/prdtStatCode"
    - "/onlineInfo/prdtStatCodeName"
    - "/onlineInfo/sellStatCode"
    - "/onlineInfo/sellStatCodeName"
    - "/onlineInfo/displayYn"
    - "/onlineInfo/saleEndText"
    - "/onlineInfo/prdtGbnCode"
    - "/onlineInfo/prdtGbnCodeName"
    # Online Info - 담당자/브랜드
    - "/onlineInfo/onlineMd/*"
    - "/onlineInfo/onlineBrand/*"
    # Emblem Info
    - "/emblemInfo/*"

  PRICE:
    - "/options"
    - "/masterInfo/packaging/*"

  INVENTORY:
    - "/onlineInfo/orderQuantity/*"
    - "/onlineInfo/orderLimits/*"
    - "/onlineInfo/sellStatCode"
    - "/reservationSale/*"
    - "/masterInfo/shelfLifeManageYn"
    - "/masterInfo/shelfLifeAvailableDays"
    - "/masterInfo/shelfLifeInboundAvailableDays"
    - "/masterInfo/shelfLifeOutboundAvailableDays"
    - "/masterInfo/clearanceYn"
    - "/masterInfo/clearanceBaseDays"
    - "/masterInfo/clearanceDisposalType"
    - "/masterInfo/disposalAllowed"
    - "/masterInfo/returnAllowed"

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
    - "/onlineInfo/*"
    - "/languageDisplayList"
    - "/emblemInfo/*"
    - "/additionalInfo/srchKeyWordText"
    - "/masterInfo/standardCategory/*"
```

### 3.2 ImpactMap 설계 원칙

1. **최소 영향 범위**: 필드 변경 시 최소한의 Slice만 재계산
2. **중복 허용**: 한 필드가 여러 SliceType에 영향 줄 수 있음 (예: `options` → PRICE, DERIVED)
3. **와일드카드 사용**: 하위 필드 전체 변경 시 `/*` 사용
4. **배열 필드**: 배열 전체 변경 시 배열 경로만 명시 (예: `/options`, `/thumbnailImages`)

---

## 4. ViewDefinition 정책

### 4.1 View 설계 원칙

**패턴**: `{ENTITY_TYPE}_{SLICE_TYPE}` - Slice 단위 1:1 매핑

각 View는 **단일 Slice**를 반환하는 것을 기본으로 하고, 복합 View는 여러 Slice View를 조합.

### 4.2 단일 Slice View (기본)

| View Name | View ID | Slice | 목적 |
|-----------|---------|-------|------|
| **PRODUCT_CORE** | `view.product.core.v1` | CORE | 기본 식별/상태/브랜드 정보 |
| **PRODUCT_PRICE** | `view.product.price.v1` | PRICE | 가격/할인 정보 |
| **PRODUCT_INVENTORY** | `view.product.inventory.v1` | INVENTORY | 재고/판매가능 정보 |
| **PRODUCT_MEDIA** | `view.product.media.v1` | MEDIA | 이미지/비디오 |
| **PRODUCT_CATEGORY** | `view.product.category.v1` | CATEGORY | 카테고리 정보 |
| **PRODUCT_INDEX** | `view.product.index.v1` | INDEX | 검색/필터용 파생 필드 |

### 4.3 복합 View (Slice 조합)

| View Name | View ID | 포함 Slice | 목적 |
|-----------|---------|-----------|------|
| **PRODUCT_DETAIL** | `view.product.detail.v1` | CORE + PRICE + MEDIA + CATEGORY + INVENTORY + INDEX | 상품 상세 (전체) |
| **PRODUCT_SEARCH** | `view.product.search.v1` | CORE + PRICE + CATEGORY + INDEX | 검색 결과 |

### 4.4 단일 Slice View 상세

#### 4.4.1 PRODUCT_CORE

**View ID**: `view.product.core.v1`  
**Slice**: CORE  
**MissingPolicy**: FAIL_CLOSED

**포함 정보**:
- 상품 식별 (prdtNo, gdsCd, gtin)
- 상품명 (prdtName, gdsNm)
- 브랜드 (brand.code, brand.krName)
- 상태/플래그 (displayYn, sellStatCode, flags.*)
- Emblem 정보 (cleanBeautyYn, veganYn 등)

#### 4.4.2 PRODUCT_PRICE

**View ID**: `view.product.price.v1`  
**Slice**: PRICE  
**MissingPolicy**: FAIL_CLOSED

**포함 정보**:
- 대표 SKU 가격 (정상가, 판매가, 할인가)
- 가격 범위 (min, max)
- 할인율
- 마진율

#### 4.4.3 PRODUCT_INVENTORY

**View ID**: `view.product.inventory.v1`  
**Slice**: INVENTORY  
**MissingPolicy**: PARTIAL_ALLOWED

**포함 정보**:
- 주문 수량 제한 (min, max, increaseUnit)
- 예약판매 정보
- 유통기한 정보
- 판매 가능 여부

#### 4.4.4 PRODUCT_MEDIA

**View ID**: `view.product.media.v1`  
**Slice**: MEDIA  
**MissingPolicy**: PARTIAL_ALLOWED

**포함 정보**:
- 썸네일 이미지 목록
- 대표 이미지
- 비디오 정보

#### 4.4.5 PRODUCT_CATEGORY

**View ID**: `view.product.category.v1`  
**Slice**: CATEGORY  
**MissingPolicy**: PARTIAL_ALLOWED

**포함 정보**:
- 노출 카테고리 (displayCategories)
- 표준 카테고리 (standardCategory)
- 카테고리 경로

#### 4.4.6 PRODUCT_INDEX

**View ID**: `view.product.index.v1`  
**Slice**: INDEX  
**MissingPolicy**: PARTIAL_ALLOWED

**포함 정보**:
- 가격 버킷 (priceBucket)
- 검색 가능 여부 (isSearchable)
- 전시 가능 여부 (isDisplayable)
- 배지 목록 (badges)
- 검색 키워드 (keywords)
- 다국어 상품명 (localizedNames)

### 4.5 복합 View 상세

#### 4.5.1 PRODUCT_DETAIL

**View ID**: `view.product.detail.v1`  
**목적**: 상품 상세 페이지 - 모든 정보 포함

**필수 Slice**: CORE, PRICE, MEDIA  
**선택 Slice**: INVENTORY, CATEGORY, INDEX  
**MissingPolicy**: FAIL_CLOSED (필수 Slice만)  
**PartialPolicy**: optionalOnly

**사용 예**:
- PDP (Product Detail Page)
- 상품 상세 API
- 앱 상품 상세

#### 4.5.2 PRODUCT_SEARCH

**View ID**: `view.product.search.v1`  
**목적**: 검색 결과 - 검색/필터에 필요한 정보

**필수 Slice**: CORE, PRICE, CATEGORY, INDEX  
**선택 Slice**: MEDIA, INVENTORY  
**MissingPolicy**: PARTIAL_ALLOWED  
**PartialPolicy**: allowed

**사용 예**:
- OpenSearch 인덱싱
- 검색 결과 리스트
- 카테고리/브랜드 페이지

### 4.6 다른 엔티티 View

| View Name | View ID | Slice | 목적 |
|-----------|---------|-------|------|
| **BRAND_CORE** | `view.brand.core.v1` | CORE | 브랜드 기본 정보 |
| **BRAND_MEDIA** | `view.brand.media.v1` | MEDIA | 브랜드 이미지 |
| **CATEGORY_CORE** | `view.category.core.v1` | CORE | 카테고리 기본 정보 |

---

## 5. 계약 파일 예시

### 5.1 RuleSet 계약 (ruleset-product-doc001.v1.yaml)

```yaml
kind: RULESET
id: ruleset.product.doc001.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

# 변경 경로 → 영향 슬라이스(SSOT)
impactMap:
  CORE:
    - "/_meta/schemaVersion"
    - "/_meta/savedAt"
    - "/_audit/*"
    - "/masterInfo/gtin"
    - "/masterInfo/gdsCd"
    - "/masterInfo/gdsNm"
    - "/masterInfo/gdsEngNm"
    - "/masterInfo/brand/*"
    - "/masterInfo/flags/*"
    - "/masterInfo/gdsStatNm"
    - "/masterInfo/buyTypNm"
    - "/masterInfo/gdsRegYmd"
    - "/masterInfo/md/*"
    - "/masterInfo/scm/*"
    - "/onlineInfo/prdtNo"
    - "/onlineInfo/prdtName"
    - "/onlineInfo/onlinePrdtName"
    - "/onlineInfo/prdtStatCode"
    - "/onlineInfo/sellStatCode"
    - "/onlineInfo/displayYn"
    - "/onlineInfo/prdtGbnCode"
    - "/onlineInfo/onlineMd/*"
    - "/onlineInfo/onlineBrand/*"
    - "/onlineInfo/appExcluPrdtYn"
    - "/emblemInfo/*"
  PRICE:
    - "/options"
    - "/masterInfo/packaging/*"
  INVENTORY:
    - "/onlineInfo/orderQuantity/*"
    - "/onlineInfo/orderLimits/*"
    - "/onlineInfo/sellStatCode"
    - "/reservationSale/*"
    - "/masterInfo/shelfLifeManageYn"
    - "/masterInfo/shelfLifeAvailableDays"
    - "/masterInfo/clearanceYn"
    - "/masterInfo/disposalAllowed"
    - "/masterInfo/returnAllowed"
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
    - "/onlineInfo/*"
    - "/languageDisplayList"
    - "/emblemInfo/*"
    - "/additionalInfo/srchKeyWordText"

joins:
  - name: brandJoin
    type: LOOKUP
    sourceFieldPath: "/masterInfo/brand/code"
    targetEntityType: BRAND
    targetKeyPattern: "BRAND#{tenantId}#{value}"
    required: false
    missingPolicy: PARTIAL_ALLOWED
    projection:
      fields:
        - fromTargetPath: "/name"
          toOutputPath: "/enriched/brand/name"
        - fromTargetPath: "/logoUrl"
          toOutputPath: "/enriched/brand/logoUrl"
        - fromTargetPath: "/country"
          toOutputPath: "/enriched/brand/country"
  - name: categoryJoin
    type: LOOKUP
    sourceFieldPath: "/displayCategories/0/sclsCtgrNo"
    targetEntityType: CATEGORY
    targetKeyPattern: "CATEGORY#{tenantId}#{value}"
    required: false
    missingPolicy: PARTIAL_ALLOWED
    projection:
      fields:
        - fromTargetPath: "/path"
          toOutputPath: "/enriched/category/path"
        - fromTargetPath: "/depth"
          toOutputPath: "/enriched/category/depth"
        - fromTargetPath: "/isActive"
          toOutputPath: "/enriched/category/isActive"

slices:
  - type: CORE
    buildRules:
      type: PassThrough
      fields:
        - "_meta"
        - "_audit"
        - "masterInfo.gtin"
        - "masterInfo.gdsCd"
        - "masterInfo.gdsNm"
        - "masterInfo.gdsEngNm"
        - "masterInfo.brand"
        - "masterInfo.flags"
        - "masterInfo.gdsStatNm"
        - "masterInfo.buyTypNm"
        - "masterInfo.gdsRegYmd"
        - "masterInfo.md"
        - "masterInfo.scm"
        - "onlineInfo.prdtNo"
        - "onlineInfo.prdtName"
        - "onlineInfo.onlinePrdtName"
        - "onlineInfo.prdtStatCode"
        - "onlineInfo.sellStatCode"
        - "onlineInfo.displayYn"
        - "onlineInfo.prdtGbnCode"
        - "onlineInfo.onlineMd"
        - "onlineInfo.onlineBrand"
        - "onlineInfo.appExcluPrdtYn"
        - "emblemInfo"
    joins:
      - name: brandJoin

  - type: PRICE
    buildRules:
      type: PassThrough
      fields:
        - "options"
        - "masterInfo.packaging"

  - type: INVENTORY
    buildRules:
      type: PassThrough
      fields:
        - "onlineInfo.orderQuantity"
        - "onlineInfo.orderLimits"
        - "onlineInfo.sellStatCode"
        - "reservationSale"
        - "masterInfo.shelfLifeManageYn"
        - "masterInfo.shelfLifeAvailableDays"
        - "masterInfo.clearanceYn"
        - "masterInfo.disposalAllowed"
        - "masterInfo.returnAllowed"

  - type: MEDIA
    buildRules:
      type: PassThrough
      fields:
        - "thumbnailImages"
        - "videoInfo"

  - type: CATEGORY
    buildRules:
      type: PassThrough
      fields:
        - "displayCategories"
        - "masterInfo.standardCategory"
    joins:
      - name: categoryJoin

  - type: INDEX
    buildRules:
      type: PassThrough
      fields:
        - "index.priceBucket"
        - "index.isOnSale"
        - "index.isSearchable"
        - "index.isDisplayable"
        - "index.badges"
        - "index.keywords"
        - "index.localizedNames"
        - "index.sortScore"

# Join fanout/Impact 계산을 위한 inverted index(SSOT)
indexes:
  - type: brand
    selector: $.enriched.brand.name
    references: BRAND
    maxFanout: 10000
  - type: category
    selector: $.displayCategories[*].sclsCtgrNo
    references: CATEGORY
    maxFanout: 50000
  - type: keyword
    selector: $.derived.keywords[*]
  - type: gtin
    selector: $.masterInfo.gtin
```

### 5.2 단일 Slice View 계약 예시

#### PRODUCT_CORE View

```yaml
kind: VIEW_DEFINITION
id: view.product.core.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_CORE
entityType: PRODUCT
description: "상품 기본 정보 (식별/상태/브랜드)"

requiredSlices:
  - CORE

optionalSlices: []

missingPolicy: FAIL_CLOSED

partialPolicy:
  allowed: false
  optionalOnly: true
  responseMeta:
    includeMissingSlices: false
    includeUsedContracts: true

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_PRICE View

```yaml
kind: VIEW_DEFINITION
id: view.product.price.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_PRICE
entityType: PRODUCT
description: "상품 가격 정보 (정상가/판매가/할인)"

requiredSlices:
  - PRICE

optionalSlices: []

missingPolicy: FAIL_CLOSED

partialPolicy:
  allowed: false
  optionalOnly: true
  responseMeta:
    includeMissingSlices: false
    includeUsedContracts: true

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_INVENTORY View

```yaml
kind: VIEW_DEFINITION
id: view.product.inventory.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_INVENTORY
entityType: PRODUCT
description: "상품 재고/판매가능 정보"

requiredSlices:
  - INVENTORY

optionalSlices: []

missingPolicy: PARTIAL_ALLOWED

partialPolicy:
  allowed: true
  optionalOnly: true
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_MEDIA View

```yaml
kind: VIEW_DEFINITION
id: view.product.media.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_MEDIA
entityType: PRODUCT
description: "상품 이미지/비디오"

requiredSlices:
  - MEDIA

optionalSlices: []

missingPolicy: PARTIAL_ALLOWED

partialPolicy:
  allowed: true
  optionalOnly: true
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_CATEGORY View

```yaml
kind: VIEW_DEFINITION
id: view.product.category.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_CATEGORY
entityType: PRODUCT
description: "상품 카테고리 정보"

requiredSlices:
  - CATEGORY

optionalSlices: []

missingPolicy: PARTIAL_ALLOWED

partialPolicy:
  allowed: true
  optionalOnly: true
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_INDEX View

```yaml
kind: VIEW_DEFINITION
id: view.product.index.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_INDEX
entityType: PRODUCT
description: "검색/필터용 파생 필드"

requiredSlices:
  - INDEX

optionalSlices: []

missingPolicy: PARTIAL_ALLOWED

partialPolicy:
  allowed: true
  optionalOnly: true
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

### 5.3 복합 View 계약 예시

#### PRODUCT_DETAIL View (복합)

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

missingPolicy: FAIL_CLOSED

partialPolicy:
  allowed: true
  optionalOnly: true
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: true

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

#### PRODUCT_SEARCH View (복합)

```yaml
kind: VIEW_DEFINITION
id: view.product.search.v1
version: 1.0.0
status: ACTIVE

viewName: PRODUCT_SEARCH
entityType: PRODUCT
description: "검색 결과 - 검색/필터에 필요한 정보"

requiredSlices:
  - CORE
  - PRICE
  - CATEGORY
  - INDEX

optionalSlices:
  - MEDIA
  - INVENTORY

missingPolicy: PARTIAL_ALLOWED

partialPolicy:
  allowed: true
  optionalOnly: false
  responseMeta:
    includeMissingSlices: true
    includeUsedContracts: true

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

### 5.4 다른 엔티티 View 예시

#### BRAND_CORE View

```yaml
kind: VIEW_DEFINITION
id: view.brand.core.v1
version: 1.0.0
status: ACTIVE

viewName: BRAND_CORE
entityType: BRAND
description: "브랜드 기본 정보"

requiredSlices:
  - CORE

optionalSlices: []

missingPolicy: FAIL_CLOSED

partialPolicy:
  allowed: false
  optionalOnly: true
  responseMeta:
    includeMissingSlices: false
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.brand.v1
  version: 1.0.0
```

#### CATEGORY_CORE View

```yaml
kind: VIEW_DEFINITION
id: view.category.core.v1
version: 1.0.0
status: ACTIVE

viewName: CATEGORY_CORE
entityType: CATEGORY
description: "카테고리 기본 정보"

requiredSlices:
  - CORE

optionalSlices: []

missingPolicy: FAIL_CLOSED

partialPolicy:
  allowed: false
  optionalOnly: true
  responseMeta:
    includeMissingSlices: false
    includeUsedContracts: false

fallbackPolicy: NONE

ruleSetRef:
  id: ruleset.category.v1
  version: 1.0.0
```

---

## 6. View 정책 요약표

### 6.1 PRODUCT 단일 Slice Views

| View Name | View ID | Slice | MissingPolicy | 용도 |
|-----------|---------|-------|---------------|------|
| **PRODUCT_CORE** | `view.product.core.v1` | CORE | FAIL_CLOSED | 기본 식별/상태/브랜드 |
| **PRODUCT_PRICE** | `view.product.price.v1` | PRICE | FAIL_CLOSED | 가격/할인 정보 |
| **PRODUCT_INVENTORY** | `view.product.inventory.v1` | INVENTORY | PARTIAL_ALLOWED | 재고/판매가능 |
| **PRODUCT_MEDIA** | `view.product.media.v1` | MEDIA | PARTIAL_ALLOWED | 이미지/비디오 |
| **PRODUCT_CATEGORY** | `view.product.category.v1` | CATEGORY | PARTIAL_ALLOWED | 카테고리 정보 |
| **PRODUCT_INDEX** | `view.product.index.v1` | INDEX | PARTIAL_ALLOWED | 검색/필터용 파생 필드 |

### 6.2 PRODUCT 복합 Views

| View Name | View ID | 필수 Slice | 선택 Slice | MissingPolicy | 용도 |
|-----------|---------|-----------|-----------|---------------|------|
| **PRODUCT_DETAIL** | `view.product.detail.v1` | CORE, PRICE, MEDIA | INVENTORY, CATEGORY, INDEX | FAIL_CLOSED | 상품 상세 (전체) |
| **PRODUCT_SEARCH** | `view.product.search.v1` | CORE, PRICE, CATEGORY, INDEX | MEDIA, INVENTORY | PARTIAL_ALLOWED | 검색 결과 |

### 6.3 BRAND / CATEGORY Views

| View Name | View ID | Slice | MissingPolicy | 용도 |
|-----------|---------|-------|---------------|------|
| **BRAND_CORE** | `view.brand.core.v1` | CORE | FAIL_CLOSED | 브랜드 기본 정보 |
| **BRAND_MEDIA** | `view.brand.media.v1` | MEDIA | PARTIAL_ALLOWED | 브랜드 이미지 |
| **CATEGORY_CORE** | `view.category.core.v1` | CORE | FAIL_CLOSED | 카테고리 기본 정보 |

---

## 7. 추가 고려사항

### 7.1 IR Builder 필요성

DOC-001의 복잡한 원본 구조를 RuleSet에서 직접 파싱하기보다, **IR Builder에서 정규화된 필드로 변환** 후 슬라이싱하는 것을 권장:

- `options[]` → `pricing.primary.*`, `pricing.min`, `pricing.max`
- `flags.*` + `sellStatCode` → `derived.isSearchable`, `derived.isDisplayable`
- `languageDisplayList` → `derived.localizedNames[lang]`
- `emblemInfo.*` → `derived.badges[]`

### 7.2 추가 SliceType 고려

필요 시 다음 SliceType 추가 고려:

- **SHIPPING**: Shipping Info 섹션 (HS 코드, 수출 카테고리 등)
- **LANGUAGE**: Language Display 섹션 (다국어 정보)
- **DESCRIPTION**: Description Info 섹션 (sellingPoint, whyWeLoveIt 등)
- **NOTICE**: Notice Info 섹션 (상품고시, 전성분)
- **GLOBAL**: Global Info 섹션 (Prop65 등)

현재는 Ship 단계에서 필요 시 변환하는 것을 권장.

### 7.3 민감정보 필터링

`_audit` (createdBy, updatedBy), `md`, `scm` 같은 내부 정보는:
- CORE Slice에는 포함 (내부 조회용)
- View/Sink로 내보낼 때 필터링 (ViewDefinition projection 또는 Ship 단계)

### 7.4 확장성

- **스키마 진화**: 필드 추가는 optional로 수용, breaking change는 schemaId 버전 업
- **로케일 확장**: `languageDisplayList` 기반 다국어 지원
- **조인 확장**: Brand/Category 외 추가 조인 필요 시 JoinSpec 추가

---

## 8. 다음 단계

1. ✅ RuleSet 계약 파일 작성 (`ruleset-product-doc001.v1.yaml`)
2. ✅ ViewDefinition 계약 파일 작성 (PDP/Search/Cart)
3. ⏳ IR Builder 구현 (DOC-001 → 정규화된 IR)
4. ⏳ ImpactMap 검증 테스트
5. ⏳ View 정책 검증 테스트
6. ⏳ Ship 단계 변환 로직 구현 (OpenSearch/Personalize)

---

**참고**: 본 문서는 DOC-001 v3.0 스키마를 기반으로 작성되었으며, 실제 구현 시 IR Builder와 Ship 단계 변환 로직과 함께 검증이 필요합니다.
