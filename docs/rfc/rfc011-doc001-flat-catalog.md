RFC-IR-031 — DOC-001 상품 마스터(Flat Catalog) 온보딩: Raw → Canonical/IR → Slice → View → Sink

Status: Draft
Created: 2026-01-26
Scope: ivm-lite / DOC-001 RawProductDocument 계약·슬라이싱·조인·뷰·싱크 설계
Audience: Catalog Platform / Search / Reco / Runtime API / Data Infra
Depends on: RFC-V4-010, RFC-IMPL-010, RFC-IMPL-011, `contracts/v1/{ruleset,view-definition,join-spec,inverted-index}.yaml`
Non-Goals: UI Authoring 폼 설계, 추천 모델 선택/학습, OpenSearch 분석기/템플릿의 세부 운영 튜닝(별도 RFC)

---

## 0. Executive Summary

본 RFC는 **DOC-001 상품마스터데이터(Flat Catalog) Raw Document**(이하 DOC-001)을 ivm-lite 파이프라인에 온보딩하기 위한 **계약(Contract)과 룰(RuleSet), 조인(JoinSpec), 뷰(ViewDefinition), 싱크(Ship to OpenSearch/Personalize) 설계**를 SSOT로 정의한다.

핵심 결론:

- **Raw(DOC-001)은 Read 경로에서 직접 사용 금지**(IR Builder 입력으로만 사용). 결정성/증분을 위해 Raw는 Canonical JSON으로 저장한다.
- Slice는 “조회/조인/증분”에 최적화된 **소수의 안정적 SliceType**만 물리화한다. (필요 시 Ship 단계에서 타겟 문서를 생성)
- Join은 `JoinSpecContract (join-spec.v1)`의 제약을 준수하는 **단일 hop LOOKUP**만 허용하고, fanout은 `InvertedIndexContract`로 관리한다.
- View는 `ViewDefinitionContract`로 **필수/선택 Slice + MissingPolicy**를 명시하고, 검색/추천 싱크는 **Ship 단계에서 타겟별 변환 + 멱등 업서트/삭제**로 반영한다.

---

## 1. 배경과 문제

### 1.1 DOC-001의 성격
- DOC-001은 Authoring Form(A0)의 원본 입력 상태를 나타내는 **Raw Document**다.
- 구조는 깊고(섹션 다수), 일부 필드가 optional이며, 화면/운영 변화로 스키마가 자주 진화한다.
- 따라서 “조회에 바로 쓰는” 형태로 고정하면 확장성과 호환성 리스크가 크다.

### 1.2 ivm-lite 관점의 요구사항
- **결정성(determinism)**: 같은 의미의 입력은 같은 결과(hash/슬라이스)여야 한다.
- **증분 처리(IVM)**: 변경된 필드가 어떤 Slice에 영향을 미치는지 ImpactMap으로 빠르게 계산해야 한다.
- **조인/팬아웃 제어**: 브랜드/카테고리 조인이 늘어도 비용/정합성을 통제해야 한다.
- **멀티 타겟**: OpenSearch(검색) + Personalize(추천) 등 서로 다른 출력 모델을 지원해야 한다.

---

## 2. 목표 / 비목표

### 2.1 Goals
- DOC-001을 안정적으로 ingest 할 수 있는 **Raw Schema ID/Version 전략** 정의
- DOC-001 → Canonical/IR 변환 규칙(정규화/필드 해석/우선순위) 정의
- Slice 설계(L1/L2/L3 개념)와 **물리 SliceType** 매핑 정의
- Brand/Category 조인 키 및 JoinSpec 설계
- PDP/Search/Cart ViewDefinition 설계(필수/선택/partial 정책)
- Ship 단계에서 OpenSearch/Personalize 타겟 문서를 생성하는 규칙 정의(멱등/삭제/컷오버)
- 앞으로 문서/타겟/로케일 확장 시의 버전/마이그레이션 전략 정의

### 2.2 Non-Goals
- 추천 모델 학습 파이프라인(Feature Store/Training/Deployment)
- 검색 품질 튜닝(스코어링/리랭킹/언어 분석기 세부)
- Authoring 입력 UI/검수 플로우

---

## 3. 용어 정의

- **Raw Document**: 원본 입력 JSON. Read 경로에서 직접 사용 금지.
- **Canonical JSON**: RFC8785 스타일의 canonicalization 결과(결정성 입력).
- **IR (Intermediate Representation)**: Raw에서 정규화/해석된 내부 표현(슬라이싱 입력).
- **Slice**: 조회/조인/증분에 필요한 최소 단위의 물리화 데이터.
- **View**: Slice를 조합해 반환하는 읽기 응답(정책은 ViewDefinition이 SSOT).
- **Sink/Ship**: Slice를 외부 시스템(OpenSearch/Personalize)로 전파하는 단계.
- **ImpactMap**: 변경 경로 → 영향을 받는 SliceType 목록.

---

## 4. 식별자/버전/멱등성 규칙 (SSOT)

### 4.1 Schema ID / Version
- **schemaId**: `raw.product.doc001.v1`
- **schemaVersion(SemVer)**: `1.0.0`
- Raw 내부의 `_meta.schemaVersion`(Int)은 UI/Authoring 변화에 따라 증가할 수 있으나,
  - ingest 레벨의 schemaVersion(SemVer)은 “파서/정규화 규칙이 호환되는 범위”를 의미한다.
  - `_meta.schemaVersion`의 증가는 IR Builder에서 **호환성 가드**로 사용한다(예: known range).

### 4.2 EntityKey / Version
권장 기본안(상품 단위):
- **EntityKey**: `PRODUCT#{tenantId}#{onlineInfo.prdtNo}`
- **version**: source-of-truth에서 monotonic increasing (예: savedAt 기반 시퀀스, 혹은 CDC offset 기반)

옵션(재고/가격이 SKU 단위로 강하면):
- SKU를 별도 엔티티로 승격(`SKU#{tenantId}#{options[].gdsCd}`)하거나,
- Product version 내에서 `options[]`를 요약 Slice로 관리한다(본 RFC는 후자를 기본으로 함).

### 4.3 Raw 저장/멱등성
`(tenantId, entityKey, version)`는 유니크(현재 raw_data 테이블 제약 준수).

- Raw payload는 canonicalize 후 저장
- payloadHash = sha256(canonical + schemaId + schemaVersion)
- 같은 키/버전에 대해 hash 불일치면 invariant violation

---

## 5. DOC-001 → Canonical/IR 변환 규칙

### 5.1 정규화 원칙
- 문자열 trim, 빈 문자열 → null(정책적으로 의미 없는 값)
- “Y/N” → boolean(예: `displayYn`)
- 상태 코드/명은 “코드”를 우선 SSOT로 저장(명은 보조)
- 날짜/시간 문자열은 **파싱 실패 시 원문 보존 + 에러 메타**(fail-open 금지; slice 정책에 따라 fail-closed 가능)

### 5.2 필드 우선순위(예시)
- 상품명: `onlineInfo.prdtName` 우선, fallback `masterInfo.gdsNm`
- 브랜드코드: `masterInfo.brand.code` 우선, fallback `onlineInfo.onlineBrand.code`
- 카테고리: `displayCategories[].sclsCtgrNo` 우선(운영/노출 카테고리), fallback `masterInfo.standardCategory.small.code`
- 대표 SKU: `options.first { rprstYn == 1 }` 없으면 `options.firstOrNull()`

### 5.3 민감정보/정책
DOC-001은 일반적으로 PII가 없어야 한다. 만약 특정 섹션에 개인/내부자 식별 정보(예: `createdBy`)가 포함될 경우:
- View/Sink로 내보내지 않는 것이 기본
- 내부 운영용 메타는 별도 Slice(또는 별도 Store)로 격리

---

## 6. Slice 설계 (개념 L1/L2/L3)와 물리 SliceType 매핑

### 6.1 설계 원칙
- Slice는 “자주 같이 읽히는 묶음” + “증분 영향 범위가 명확한 묶음”으로 나눈다.
- SliceType enum 폭증을 피하기 위해, **물리 Slice는 제한된 타입만 저장**하고,
  - OpenSearch/Personalize 같은 타겟 전용 문서는 **Ship 단계에서 생성**한다.

### 6.2 물리 SliceType(현재 enum 기반)
이 RFC의 “물리 저장 Slice”는 아래 6개로 제한한다.

- **CORE**: 식별/명칭/브랜드/상태/플래그(조회 공통)
- **PRICE**: 가격/할인/마진(대표 SKU + 요약)
- **INVENTORY**: 판매 가능/주문 제한/재고 관련(대표 SKU + 요약)
- **MEDIA**: 썸네일/비디오 요약
- **CATEGORY**: 노출 카테고리/표준 카테고리 요약
- **DERIVED**: 파생 필드(버킷/정규화 상태/검색 필터용)

조인 결과는 별도 SliceType으로 저장하지 않고,
- `CORE/CATEGORY/DERIVED` 내에 projection으로 병합하거나,
- View 단계에서 optional로 합성한다(JoinSpec 정책에 따라).

---

## 7. RuleSet 설계 (DOC-001용)

### 7.1 RuleSet ID
- `ruleset.product.doc001.v1` / `1.0.0`

### 7.2 ImpactMap (예시; JSON Pointer 기준)
- `/onlineInfo/prdtName` 변경 → CORE
- `/masterInfo/flags/*` 변경 → CORE, DERIVED
- `/options[*]/dcSelprcUprc` 변경 → PRICE, DERIVED
- `/thumbnailImages[*]` 변경 → MEDIA
- `/displayCategories[*]/sclsCtgrNo` 변경 → CATEGORY, DERIVED

### 7.3 Join(LOOKUP) 설계

DOC-001의 조인은 “검색/추천/상세”에서 가장 많이 쓰이는 **Brand, Category**만 1-hop LOOKUP으로 시작한다.

- **Brand Join**
  - source: `/masterInfo/brand/code` (fallback: `/onlineInfo/onlineBrand/code`)
  - target: `BRAND#{tenantId}#{value}`
  - required: false (브랜드 미정/미연동 상품 허용)
  - projection(예): `/name`, `/logoUrl`, `/country` → CORE 내 `/enriched/brand/*`로 복사
- **Category Join**
  - source: `/displayCategories/0/sclsCtgrNo` (fallback: `/masterInfo/standardCategory/small/code`)
  - target: `CATEGORY#{tenantId}#{value}`
  - required: false (일부 상품은 노출 카테고리 미정 가능)
  - projection(예): `/path`, `/depth`, `/isActive` → CATEGORY 내 `/enriched/category/*`

### 7.4 RuleSet YAML (초안; 계약이 법)

아래는 `contracts/v1/ruleset.v1.yaml` 형식을 따르는 “DOC-001용 RuleSet”의 초안이다.
실제 필드/경로는 IR 정규화 규칙과 함께 고정되며, `impactMap`은 운영 중 증분 계산의 SSOT가 된다.

```yaml
kind: RULESET
id: ruleset.product.doc001.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

# 변경 경로 → 영향 슬라이스(SSOT)
impactMap:
  CORE:
    - "/onlineInfo/prdtNo"
    - "/onlineInfo/prdtName"
    - "/masterInfo/gdsNm"
    - "/masterInfo/brand/code"
    - "/masterInfo/flags"
    - "/onlineInfo/displayYn"
    - "/onlineInfo/sellStatCode"
    - "/onlineInfo/prdtStatCode"
  PRICE:
    - "/options"
  INVENTORY:
    - "/onlineInfo/orderQuantity"
    - "/onlineInfo/orderLimits"
    - "/onlineInfo/sellStatCode"
  MEDIA:
    - "/thumbnailImages"
    - "/videoInfo"
  CATEGORY:
    - "/displayCategories"
    - "/masterInfo/standardCategory"
  DERIVED:
    - "/masterInfo/flags"
    - "/options"
    - "/displayCategories"
    - "/onlineInfo"

joins: []

slices:
  - type: CORE
    buildRules:
      type: PassThrough
      fields:
        - "onlineInfo.prdtNo"
        - "onlineInfo.prdtName"
        - "masterInfo.gdsNm"
        - "masterInfo.brand"
        - "masterInfo.flags"
        - "onlineInfo.displayYn"
        - "onlineInfo.prdtStatCode"
        - "onlineInfo.sellStatCode"
        - "onlineInfo.appExcluPrdtYn"
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

  - type: PRICE
    buildRules:
      type: PassThrough
      fields:
        # 대표 SKU + 요약은 IR builder에서 정규화 후 필드로 제공하는 것을 권장
        - "pricing.primary.normal"
        - "pricing.primary.sale"
        - "pricing.primary.discountRate"
        - "pricing.min"
        - "pricing.max"

  - type: INVENTORY
    buildRules:
      type: PassThrough
      fields:
        - "availability.isSellable"
        - "availability.sellStatus"
        - "availability.orderQuantity"
        - "availability.orderLimits"

  - type: MEDIA
    buildRules:
      type: PassThrough
      fields:
        - "media.thumbnails"
        - "media.videos"

  - type: CATEGORY
    buildRules:
      type: PassThrough
      fields:
        - "category.display.categoryId"
        - "category.display.path"
        - "category.standard"
    joins:
      - name: categoryJoin
        type: LOOKUP
        sourceFieldPath: "/category/display/categoryId"
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

  - type: DERIVED
    buildRules:
      type: PassThrough
      fields:
        - "derived.priceBucket"
        - "derived.isOnSale"
        - "derived.isSearchable"
        - "derived.isDisplayable"
        - "derived.badges"
        - "derived.keywords"

# Join fanout/Impact 계산을 위한 inverted index(SSOT)
indexes:
  - type: brand
    selector: $.enriched.brand.name
  - type: category
    selector: $.category.display.categoryId
  - type: keyword
    selector: $.derived.keywords[*]
```

> 주의: 위 RuleSet 예시는 “IR 단계에서 이미 정규화된 필드(pricing/availability/media/category/derived)”를 전제로 한다.
> 즉, DOC-001의 복잡한 원본 구조를 RuleSet에서 직접 파싱하기보다, **IR Builder에서 CanonicalProduct를 만든 뒤** 슬라이싱 엔진은 안정적인 IR 필드만 다루도록 하는 것이 확장성 측면에서 안전하다.

---

## 8. ViewDefinition 설계 (PDP/Search/Cart)

### 8.1 View ID / Version
- `view.product.pdp.v2` / `2.0.0`
- `view.product.search.v2` / `2.0.0`
- `view.product.cart.v2` / `2.0.0`

### 8.2 정책 원칙
- PDP: CORE/PRICE/MEDIA는 fail-closed(없으면 응답 실패)
- Search: partial 허용(누락 시 노출 정책은 검색 어플리케이션이 결정)
- Cart: CORE/PRICE fail-closed, MEDIA optional

### 8.3 ViewDefinition YAML (초안)

#### PDP
```yaml
kind: VIEW_DEFINITION
id: view.product.pdp.v2
version: 2.0.0
status: ACTIVE

requiredSlices: [CORE, PRICE, MEDIA]
optionalSlices: [INVENTORY, CATEGORY, DERIVED]
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

#### Search
```yaml
kind: VIEW_DEFINITION
id: view.product.search.v2
version: 2.0.0
status: ACTIVE

requiredSlices: [CORE, PRICE, CATEGORY, DERIVED]
optionalSlices: [MEDIA, INVENTORY]
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

#### Cart
```yaml
kind: VIEW_DEFINITION
id: view.product.cart.v2
version: 2.0.0
status: ACTIVE

requiredSlices: [CORE, PRICE]
optionalSlices: [MEDIA]
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

---

## 9. Ship (Sink) 설계: OpenSearch / Personalize

### 9.1 공통 원칙
- Ship은 “슬라이스를 그대로 내보내기”가 아니라, **타겟별 문서 스키마로 변환**한다.
- 문서 변환은 결정적이어야 하며, 동일 입력(동일 slice hash set) → 동일 출력이 되어야 한다.
- 삭제(Tombstone)는 타겟에서도 “멱등 삭제”로 반영한다.

### 9.2 OpenSearch (검색)

#### 9.2.1 인덱스/별칭/컷오버
- 인덱스: `ivm-{tenant}-products-YYYYMMDDHHmmss` (배포 단위로 새 인덱스)
- 별칭: `ivm-{tenant}-products` (읽기/검색은 별칭만 사용)
- Cutover:
  - ship 완료 후 alias swap (atomic)
  - 실패 시 이전 alias 유지(roll-forward만 허용)

#### 9.2.2 Document ID
- `tenant__PRODUCT#{tenant}#{prdtNo}` (URL-safe)

#### 9.2.3 Target Document 스키마(요약; SSOT는 별도 “search-doc contract”로 분리 가능)
권장 필드:
- `id`, `tenantId`, `prdtNo`
- `name`(검색 텍스트), `brandName`, `categoryPath`
- `price`(대표가), `minPrice`, `maxPrice`, `isOnSale`
- `isSellable`, `sellStatus`, `displayable`
- `keywords[]`, `badges[]`
- `thumbnailUrl`, `videoIds[]`
- `updatedAtVersion`

데이터 소스:
- `CORE` + `PRICE` + `CATEGORY` + `DERIVED` (+ optional `MEDIA/INVENTORY`)

#### 9.2.4 멱등/재시도
- bulk upsert(배치) 기본
- 동일 문서 ID에서 동일 content hash면 noop 허용
- 타임아웃/재시도는 orchestration(ShipWorkflow)에서 정책화

### 9.3 Personalize (추천)

#### 9.3.1 Items 데이터셋
- itemId: 기본은 `prdtNo` (추천 단위가 SKU면 별도 RFC로 승격)
- properties:
  - `brandCode`, `categoryId`, `priceBucket`, `isOnSale`, `badges`, `keywords`
  - 장문/HTML(상세설명) 등은 제외(변동/노이즈/비용)

#### 9.3.2 삭제 정책
Personalize는 “삭제”가 제약적일 수 있으므로:
- 기본은 `isActive=false` 같은 플래그로 비활성화(추천에서 제외)
- 물리 삭제는 운영 정책으로 제한(별도 데이터셋 관리 필요)

#### 9.3.3 Interactions
클릭/구매/장바구니/찜은 DOC-001이 아니라 **행동 이벤트 스트림**에서 수집해야 한다(Outbox/Event).
본 RFC에서는 Items 업데이트까지만 범위로 둔다.

---

## 10. Contract(계약) 구성안 (추가/변경)

### 10.1 신규 계약(권장)
아래 파일들은 “추가해야 할 계약”의 권장 위치/명명이다.

- `src/main/resources/contracts/v1/entity-raw-product-doc001.v1.yaml`
  - kind: `ENTITY_SCHEMA` (v1 호환)
  - id: `raw.product.doc001.v1` (ingest schemaId와 동일)
- `src/main/resources/contracts/v1/ruleset-product-doc001.v1.yaml`
  - kind: `RULESET`
  - id: `ruleset.product.doc001.v1`
- `src/main/resources/contracts/v1/view-product-pdp.v2.yaml`
- `src/main/resources/contracts/v1/view-product-search.v2.yaml`
- `src/main/resources/contracts/v1/view-product-cart.v2.yaml`

> 참고: 현 구현의 ingest는 “JSON 파싱 가능 여부”만 검사한다.
> 계약 기반의 강한 검증(필수 필드/타입/범위)은 IR Builder 단계 또는 `tooling`(validate)에서 강제하는 것을 권장한다.

### 10.2 Raw 스키마 계약(초안; 최소 검증)
DOC-001은 전체 스키마가 방대하므로, v1에서는 “키 필드 + 중요한 섹션 존재”만 강제하고, 나머지는 점진적으로 강화한다.

```yaml
kind: ENTITY_SCHEMA
id: raw.product.doc001.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

fields:
  - name: _meta.schemaVersion
    type: int
    required: true
  - name: onlineInfo.prdtNo
    type: string
    required: true
  - name: onlineInfo.prdtName
    type: string
    required: false
  - name: masterInfo.gdsNm
    type: string
    required: true
  - name: masterInfo.brand.code
    type: string
    required: true
  - name: displayCategories
    type: list<object>
    required: false

ruleSetRef:
  id: ruleset.product.doc001.v1
  version: 1.0.0
```

> 주의: 현재 `ENTITY_SCHEMA` 계약 포맷이 “중첩 path”를 1급으로 지원하는지 여부는 구현에 따라 다르다.
> 중첩 검증을 강하게 하려면 (a) 계약 포맷 확장(RFC6901 path 지원) 또는 (b) IR Builder에서 강제 검증을 채택한다.

---

## 11. 확장성 / 진화 전략 (SOTA)

### 11.1 DOC-001 스키마 진화
- additive(필드 추가): 기존 contract 유지 + optional로 수용
- rename/breaking:
  - `raw.product.doc001.v2`로 schemaId를 올리고,
  - RuleSet/ViewDefinition은 신규 버전으로 병행 운영(dual-run 가능)
- 제거(deprecate): 일정 기간 “읽기/검색”에서 사용 중인 필드가 제거되지 않도록, contract에 deprecation window를 둔다.

### 11.2 로케일/다국어
- `languageDisplayList`는 Search 텍스트에 영향을 줄 수 있으므로,
  - DERIVED에 `localizedNames[lang]`, `isSearchableByLang[lang]` 형태로 정규화
- OpenSearch는 멀티필드(ko/en)로 저장하되, analyzer 튜닝은 별도 RFC

### 11.3 조인 확장
- Join depth는 1로 유지(JoinSpecContract 제약).
- 추가 조인이 필요하면:
  - “조인 대상”을 별도 Slice로 미리 요약(Brand/Category summary)
  - fanout은 inverted index로 제어

### 11.4 멀티 타겟 확장
- “타겟 문서”는 Slice로 저장하지 않고 Ship 단계에서 생성(현재 설계).
- 신규 타겟 추가는:
  - SinkSpec(DSL) + 변환 함수(ShipWorkflow step) + 운영 계약(문서 스키마) 추가로 해결

---

## 12. 테스트 / 검증 전략

필수 검증:
- 결정성: 같은 의미의 DOC-001 → 동일 Slice hash
- ImpactMap: 필드 변경이 최소 slice만 재계산하도록 매핑 검증
- Join 결정성: 동일 입력에서 동일 join 결과(정렬/선택 규칙 고정)
- View 정책: MissingPolicy/PartialPolicy가 계약대로 동작
- Ship 멱등성: 동일 입력은 동일 문서 upsert/삭제 결과

권장 테스트 구성:
- Contract Golden(문서 샘플 → slice 스냅샷)
- E2E: Ingest → Outbox → Slicing → Query(View) → Ship(OpenSearch/Personalize stub)

---

## 13. 롤아웃 플랜 (운영)

1) 계약 등록(Registry) + validateContracts 통과  
2) staging tenant에서 DOC-001 샘플 backfill + slice 생성 검증  
3) OpenSearch 신규 인덱스에 ship(별칭 미스위치)  
4) Search 품질/필터/카테고리/브랜드 facet 검증  
5) alias cutover(atomic)  
6) Personalize items 반영(비활성화 정책 포함)  
7) 모니터링(에러율/지연/인덱스 사이즈/ship 실패 재시도)  

---

## 14. Open Questions

- `ENTITY_SCHEMA` 계약이 중첩 path 검증을 1급 지원해야 하는가? (지원한다면 포맷 확장 RFC 필요)
- Product 단위 vs SKU 단위 추천(itemId) 결정(추천 품질/운영 비용 트레이드오프)
- “검색 문서 스키마(contract)”를 별도 kind로 만들지, 문서로만 관리할지
- doc001 내 `audit`/`md/scm` 같은 내부 필드를 어느 단계에서 필터링할지(PII/내부정보 정책)

