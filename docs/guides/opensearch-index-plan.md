# OpenSearch 인덱스 계획 v2 (2-인덱스 SOTA)

> IVM-Lite → OpenSearch 인덱싱 전략. Static(검색/필터/카탈로그) + Dynamic(고빈도 변동) 분리 구조.

---

## 목차

| 절 | 제목 |
|----|------|
| 1 | 최종 전략 |
| 2 | 인덱스 분리 설계 |
| 3 | Static 인덱스 설계 |
| 4 | Dynamic 인덱스 설계 |
| 5 | Projection SSOT v1 |
| 6 | 정규화 규칙 SSOT |
| 7 | Query Template SSOT v1 |
| 8 | 정렬 규칙 SSOT |
| 9 | 인덱싱 파이프라인 |
| 10 | Join 구현 옵션 |
| 11 | Reindex/Update Runbook |
| 12 | 검증 쿼리 세트 v1 (Static 20 + Dyn 20 + Join 10 + 실데이터 v1.1 + 페이징 SSOT) |
| 13 | 단계별 작업계획 |
| 14 | 적용 체크리스트 |
| 15 | 환경 변수 |
| 16 | 참고 |

---

## 1. 최종 전략

### 1-1. 핵심 결론

**"상품 정적 정보(검색/필터/카탈로그)"와 "고빈도 변동 정보(재고/가격/리뷰/랭킹)"를 분리한 2-인덱스 구조가 SOTA**

- **리트리빙**: Static 인덱스로 후보 추출 → Dynamic 인덱스로 실시간 상태 join(앱 레벨) → 필터/정렬 완성
- **결과**: reindex 비용과 업데이트 비용 분리, 검색 품질/운영 안정성 동시 확보

### 1-2. 산출물 6종

| 산출물 | 설명 |
|--------|------|
| Index SSOT v1 | 네이밍/alias/버전 규칙 |
| Mapping SSOT v1 | static/dynamic 매핑 템플릿 |
| Projection SSOT v1 | Slice/View → Index 문서 변환 규칙 |
| Query Template SSOT v1 | 검색/리스팅/정확조회/유사검색 DSL |
| Reindex Runbook v1 | blue/green + 검증 쿼리 세트 |
| Update Runbook v1 | 고빈도 업데이트 bulk 정책 + 충돌/역전 방지 |

---

## 2. 인덱스 분리 설계

### 2-1. 인덱스 2개

| 인덱스 | 물리 인덱스 패턴 | 용도 |
|--------|------------------|------|
| **Static** | `ivm-products-static-{tenantId}-v1-{stamp}` | 카탈로그/검색/필터/집계 (schemaVersion=v1) |
| **Dynamic** | `ivm-products-dyn-{tenantId}-v1-{stamp}` | 재고/가격/리뷰/랭킹 등 고빈도 상태 (schemaVersion=v1) |

### 2-2. Alias

| 용도 | Alias | 대상 |
|------|-------|------|
| Static read | `ivm-products-{tenantId}` | 최신 Static 물리 인덱스 |
| Static write | `ivm-products-{tenantId}__write` | 최신 Static 물리 인덱스 |
| Dyn read | `ivm-products-dyn-{tenantId}` | 최신 Dyn 물리 인덱스 |
| Dyn write | `ivm-products-dyn-{tenantId}__write` | 최신 Dyn 물리 인덱스 |

### 2-3. 문서 ID SSOT

| 인덱스 | `_id` | 비고 |
|--------|-------|------|
| Static | `{tenantId}__{entityKey}` | 예: `oliveyoung__PRODUCT:oliveyoung:UA11279226` |
| Dyn | `{tenantId}__{entityKey}` | Static과 1:1 동일 키 |

### 2-4. 왜 2개로 쪼개나

| 구분 | Static | Dynamic |
|------|--------|---------|
| **주요 작업** | 재색인(스키마/분석기/동의어/카테고리 변경) | 부분 업데이트(재고/가격/리뷰/랭킹) |
| **변동 빈도** | 낮음 | 초당/분당 |
| **한 인덱스에 섞을 때 문제** | 검색 품질 변경 = 대규모 reindex | 재고 업데이트 = segment churn |
| **분리 효과** | reindex 비용 독립 | 업데이트 비용 독립, 검색 품질과 운영이 독립 진화 |

---

## 3. Static 인덱스 설계

### 3-1. Static 문서에 담는 것

- 상품명/브랜드/카테고리/속성/검색키워드/뱃지/미디어(썸네일)
- "필터/집계" 가능한 형태로 **flatten 필드** 제공
- 옵션 nested는 '정확 옵션 검색' 필요 시에만 **최소 유지** (sku, name만)

### 3-2. Static 필드 그룹 (SSOT)

| 그룹 | 필드 예시 |
|------|-----------|
| **Identity** | tenantId, entityKey, uaCode, productId, updatedAt (docVersion은 선택) |
| **Search** | title_ko, title_en, brand_ko, brand_en, search_keywords |
| **Facet** | category_display, category_std, attr_codes, attr_kv, attr_formulation, attr_skin_type, attr_main_functions, attr_ingredients, badge_* |
| **Media** | thumb_url |
| **Minimal nested** | options(sku, name) — 가격/재고는 Dyn으로 이동 |

### 3-3. Static 매핑 원칙

- `dynamic=false` + template로 고정
- text는 multi-field(raw keyword) 필수
- facet용은 keyword만 사용(집계 안정화)

---

## 4. Dynamic 인덱스 설계

### 4-1. Dynamic 문서에 담는 것

| 영역 | 필드 |
|------|------|
| **재고** | sellable, in_stock, stock_qty, option_count, option_in_stock_count |
| **가격** | price_min, price_max, dc_price_min, discount_rate_max |
| **리뷰/평점** | review_cnt, rating_avg |
| **랭킹/인기도** | sales_7d, sales_30d, view_7d, wish_cnt (있으면) |
| **프로모션** | promo_active, promo_tags (있으면) |

### 4-2. Dynamic 매핑 원칙

- 숫자/boolean 위주로 단순
- 업데이트 충돌 방지: `dynVersion`(long) + `updatedAt`(date)
- doc 크기 최소화(업데이트 비용 최소화)

### 4-3. 업데이트 정책

- Dyn 인덱스는 **부분 update(upsert)** 허용
- **역전 방지 SSOT**: Producer가 dynVersion 단조 증가를 보장. 가능하면 OpenSearch external versioning 사용(OS가 낮은 버전 거부). 책임은 Producer에 있음.
- Bulk update는 "상품ID 기준 묶음"으로 일정 주기 flush

### 4-4. 랭킹 SSOT (2-인덱스 범위)

- Dyn 랭킹 필드는 **rank_score_base**를 최소 단위 SSOT로 둔다. 윈도우별 점수 필요 시 rank_score_24h, rank_score_7d 등 2~3개까지만 허용(필드 폭발 방지).
- 랭킹 페이지는 **"리스트 저장형"이 아니라 "점수 정렬형"**으로 제공한다.
- 카테고리 베스트는 static filter 후보에서 dyn rank_score로 정렬하며, 정확 topN이 필요하면 후보 크기(topN 후보)를 상향한다(기본 2000 권장, 운영 상황에 맞게 조정).
- 향후 **"리스트 자체 SSOT(수동 큐레이션/스폰서 슬롯/편집 고정)"** 요구가 생기면 랭킹 전용 저장소(별도 인덱스 또는 Redis)를 확장한다. v2 범위에서는 제외한다.

---

## 5. Projection SSOT v1

### 5-1. Static Projection (PRODUCT_SEARCH View → ivm-products-{tenantId})

#### 5-1-1. Identity / 메타

| Source (View) | Target (Static) | Type | Rule |
|---------------|-----------------|------|------|
| tenantId (SinkRule/Context) | tenantId | keyword | 고정 |
| entityKey | entityKey | keyword | 고정 |
| uaCode | uaCode | keyword | 고정 |
| uaCode | productId | keyword | 기본 uaCode 재사용(별도 productId 규칙 있으면 교체) |
| (선택) | docVersion | long | Static은 전량 backfill/reindex 중심이라 생략 가능. 필요 시 단조 증가 규칙 별도 |
| schema version | schemaVersion | keyword | v1 (매핑 스키마 버전) |
| ingest timestamp | updatedAt | date | UTC ISO-8601 |

#### 5-1-2. 검색 텍스트 (검색 품질 SSOT)

| Source (View) | Target (Static) | Type | Rule |
|---------------|-----------------|------|------|
| onlineInfo.prdtName | title_ko | text(ko_nori) + keyword(raw) | 우선값 |
| masterInfo.gdsNm | title_ko | text(ko_nori) + keyword(raw) | onlineInfo.prdtName 없을 때 fallback |
| masterInfo.gdsEngNm | title_en | text(en_std) + keyword(raw) | 없으면 null |
| masterInfo.brand.krName | brand_ko | text(ko_nori) + keyword(raw) | 없으면 null |
| masterInfo.brand.enName | brand_en | text(en_std) + keyword(raw) | 없으면 null |
| additionalInfo.srchKeyWordText | search_keywords | text(ko_nori) | 쉼표/공백 split 후 join(정규화) |

#### 5-1-3. 필터/집계 (Flatten SSOT)

| Source (View) | Target (Static) | Type | Rule |
|---------------|-----------------|------|------|
| masterInfo.brand.code | brand_code | keyword | 고정 |
| displayCategories[].sclsCtgrNo | category_display | keyword[] | 중복 제거, 정렬(결정성) |
| masterInfo.standardCategory.large.code | category_std | keyword[] | L:{code} push |
| masterInfo.standardCategory.medium.code | category_std | keyword[] | M:{code} push |
| masterInfo.standardCategory.small.code | category_std | keyword[] | S:{code} push |
| attributes[].attrCode | attr_codes | keyword[] | 중복 제거, 정렬 |
| attributes[] | attr_kv | keyword[] | attrCode={normalizedValue} 형태로 flatten |
| attributes[] (attrCode별) | attr_formulation, attr_skin_type, attr_main_functions, attr_ingredients | keyword[] | UI Refine 패널용. 아래 attrCode 매핑 참조 |
| emblemInfo.veganYn | badge_vegan | boolean | null이면 false 취급 금지, null 유지 또는 false 정책 SSOT화 |
| emblemInfo.cleanBeautyYn | badge_clean | boolean | 동일 |
| emblemInfo.crueltyFreeYn | badge_cruelty_free | boolean | 동일 |

#### 5-1-4. 미디어 (리스팅 최소)

| Source (View) | Target (Static) | Type | Rule |
|---------------|-----------------|------|------|
| thumbnailImages[0].url or 대표 썸네일 규칙 | thumb_url | keyword | 절대/상대 규칙은 별도 SSOT에 따름 |

#### 5-1-5. 옵션 (정확 조회/옵션명 검색용, 가격/재고는 Dyn로 이동)

| Source (View) | Target (Static.options[]) | Type | Rule |
|---------------|---------------------------|------|------|
| options[].gdsCd | options[].sku | keyword | 원본 유지 |
| options[].gdsNm | options[].name | text(ko_nori)+keyword(raw) | 원본 유지 |

#### 5-1-6. Static에서 제거 항목 (→ Dyn로 이동)

| Source (View) | Target | Reason |
|---------------|--------|--------|
| options[].gdsSelprcUprc / dcSelprcUprc | Dyn | 고빈도 변경 + 정렬/필터 핵심 |
| options[].existYn, onlineInfo.sellStatCode, orderQuantity 등 | Dyn | 재고/판매상태 고빈도 |
| review_cnt, rating_avg, sales_* | Dyn | 고빈도 + 집계/정렬 |

---

### 5-2. Dyn Projection (상태 스트림/집계 → ivm-products-dyn-{tenantId})

#### 5-2-1. Identity / 메타

| Source | Target (Dyn) | Type | Rule |
|--------|--------------|------|------|
| tenantId | tenantId | keyword | 고정 |
| entityKey | entityKey | keyword | 고정 |
| uaCode | uaCode | keyword | 고정 |
| dyn event version | dynVersion | long | 단조 증가(역전 방지 키) |
| dyn ingest timestamp | updatedAt | date | UTC ISO-8601 |
| schema version | schemaVersion | keyword | v1 (매핑 스키마 버전) |

#### 5-2-2. 재고/판매가능

| Source | Target (Dyn) | Type | Rule |
|--------|--------------|------|------|
| onlineInfo.sellStatCode + 옵션 existYn + 품절 정책 | sellable | boolean | "판매가능" 정의 SSOT화 필요 |
| 옵션 재고 요약(any/none) | in_stock | boolean | any-in-stock 권장 |
| orderQuantity or stock qty | stock_qty | integer | 없으면 null |
| options existYn/qty | option_count, option_in_stock_count | integer | flatten. optionCount, inStockCount |

#### 5-2-3. 가격 (정렬/필터 SSOT)

| Source | Target (Dyn) | Type | Rule |
|--------|--------------|------|------|
| options[].gdsSelprcUprc | price_min | integer | 옵션 중 최소 |
| options[].gdsSelprcUprc | price_max | integer | 옵션 중 최대 |
| options[].dcSelprcUprc | dc_price_min | integer | 옵션 중 최소(없으면 null) |
| (price, dc_price) | discount_rate_max | integer | 최대 할인율(없으면 0 또는 null 정책) |

#### 5-2-4. 리뷰/평점/랭킹 (있으면)

| Source | Target (Dyn) | Type | Rule |
|--------|--------------|------|------|
| review system aggregate | review_cnt | integer | null 허용 |
| review system aggregate | rating_avg | float | null 허용 |
| sales aggregate | sales_7d | integer | null 허용 |
| sales aggregate | sales_30d | integer | null 허용 |
| ranking pipeline | rank_score_base | float | static score와 merge 규칙 별도 |

#### 5-2-5. 옵션별 가격/재고 (정밀 필터 필요 시만)

| Source | Target (Dyn.options[]) | Type | Rule |
|--------|------------------------|------|------|
| options[].gdsCd | options[].sku | keyword | static.options와 join 키 |
| options[].gdsSelprcUprc | options[].price | integer | |
| options[].dcSelprcUprc | options[].dc_price | integer | |
| options[].existYn/qty | options[].in_stock | boolean | |

---

## 6. 정규화 규칙 SSOT

### 6-1. 문자열 정규화 (normalizeValue)

- trim, 연속 공백 축약
- 대소문자: facet용은 소문자 normalizer 적용 가능(언어별 주의)
- 특수문자: 값 검색(text)과 facet(keyword) 분리 처리

| 용도 | 규칙 |
|------|------|
| 검색(text) | 원문 보존 중심 |
| facet(keyword) | normalizeValue 적용 |

### 6-2. attr_kv 생성 규칙

```
attr_kv = "{attrCode}={normalizeValue(attrValue)}"
```

예: `2=수분크림 제형`, `6=모든피부타입`

- UI facet은 **attr_kv**, **attr_codes**, 또는 **attrCode별 필드** 사용

### 6-3. attrCode → UI Facet 매핑 (Olive Young)

| attrCode | Static 필드 | UI Facet |
|----------|-------------|----------|
| 2 | attr_formulation | Formulation (제형타입) |
| 6 | attr_skin_type | Skin Type (추천피부타입) |
| 42 | attr_main_functions | Main Functions / Skin Concern (주요기능) |
| 81 | attr_ingredients | Featured Ingredients (주요성분) |

- 위 매핑에 없는 attrCode는 **attr_kv**로만 집계 (예: SPF, UVA Star Rating 등)

### 6-4. 카테고리 표준화 (category_std)

- `L:{large.code}`, `M:{medium.code}`, `S:{small.code}`
- code 누락 시 해당 레벨만 omit
- 집계/필터는 **category_std로만 처리**, name은 표시용으로만 사용

---

## 7. Query Template SSOT v1

### 7-1. API 계약 (요청/응답 스키마)

#### 7-1-1. Search API (Template A)

**Endpoint**: `POST /v1/products/search`

**Request schema**:

| Field | Type | Required | Note |
|-------|------|----------|------|
| tenantId | string | Y | |
| q | string | N | 없으면 listing으로 처리 권장 |
| filters.categoryDisplay | string[] | N | display category ids |
| filters.categoryStd | string[] | N | L:* M:* S:* |
| filters.brandCodes | string[] | N | |
| filters.badges.vegan | boolean | N | |
| filters.badges.clean | boolean | N | |
| filters.badges.crueltyFree | boolean | N | |
| filters.attrCodes | string[] | N | OR/AND 정책 SSOT화 |
| filters.attrKv | string[] | N | OR/AND 정책 SSOT화 |
| filters.attrFormulation | string[] | N | attr_formulation 값 |
| filters.attrSkinType | string[] | N | attr_skin_type 값 |
| filters.attrMainFunctions | string[] | N | attr_main_functions 값 |
| filters.attrIngredients | string[] | N | attr_ingredients 값 |
| dynFilters.sellable | boolean | N | 기본 true 권장 |
| dynFilters.inStock | boolean | N | 기본 true 권장(리스팅) |
| dynFilters.priceMin | integer | N | |
| dynFilters.priceMax | integer | N | |
| sort | object | N | 아래 8절 정렬 규칙 SSOT 참조 |
| page.size | integer | N | default 40 |
| page.searchAfter | string[] | N | search_after 용 |

**Response schema**:

| Field | Type | Note |
|-------|------|------|
| items[] | object | static + dyn merge 결과 |
| items[].entityKey | string | |
| items[].title | string | title_ko 우선 |
| items[].brand | object | code + name |
| items[].thumbUrl | string | |
| items[].price | object | dyn 기반 |
| items[].stock | object | dyn 기반 |
| facets | object | static facet 결과 |
| page.nextSearchAfter | string[] | search_after 반환 |

#### 7-1-2. Listing API (Template B)

`POST /v1/products/listing`

- Request: tenantId + static filters + dynFilters + sort + page
- q 없음(또는 empty)
- facets 동일 제공

#### 7-1-3. Exact API (Template C)

- `GET /v1/products/{tenantId}/{entityKey}`
- 또는 `GET /v1/products/by-ua/{tenantId}/{uaCode}`
- 응답: static 원문(표시용) + dyn 상태

#### 7-1-4. Hybrid Rerank API (Template D, 선택)

- `POST /v1/products/similar` 또는 rerank 전용 엔드포인트
- **패턴**: Static 후보 200~500개 뽑고 → Dyn/벡터/룰로 rerank
- Request: tenantId + seed entityKey + optional filters
- 응답: items + explanation(optional)

---

### 7-2. DSL 생성 규칙 (Static → Dyn merge)

#### 7-2-1. 단계 1: Static 후보 검색

- **index**: `ivm-products-{tenantId}` (read alias)
- **query**: q 존재 시 multi_match, q 없음 시 match_all + static filters
- **static filters**: category_display, category_std, brand_code, badge_*, attr_codes/attr_kv, attr_formulation/attr_skin_type/attr_main_functions/attr_ingredients
- **aggs**: brand_code, category_display, attr_formulation, attr_skin_type, attr_main_functions, attr_ingredients (attrCode별 필드 권장. attr_kv는 include regex로 제한적 사용)

**Static DSL 샘플 (Template A)**:

```json
{
  "size": 200,
  "_source": ["entityKey","uaCode","title_ko","title_en","brand_code","brand_ko","thumb_url","category_display","attr_codes","attr_formulation","attr_skin_type","attr_main_functions","attr_ingredients","badge_vegan","badge_clean","badge_cruelty_free"],
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "비건 선크림",
            "fields": ["title_ko^3","brand_ko^2","search_keywords","title_en","brand_en"],
            "type": "best_fields",
            "operator": "and"
          }
        }
      ],
      "filter": [
        { "term": { "tenantId": "oliveyoung" } },
        { "terms": { "category_display": ["100000200"] } },
        { "term": { "badge_vegan": true } },
        { "terms": { "attr_codes": ["FORM_GEL"] } }
      ]
    }
  },
  "aggs": {
    "brand": { "terms": { "field": "brand_code", "size": 50 } },
    "category": { "terms": { "field": "category_display", "size": 50 } },
    "formulation": { "terms": { "field": "attr_formulation", "size": 30 } },
    "skin_type": { "terms": { "field": "attr_skin_type", "size": 30 } },
    "main_functions": { "terms": { "field": "attr_main_functions", "size": 50 } },
    "ingredients": { "terms": { "field": "attr_ingredients", "size": 50 } }
  },
  "sort": [
    { "_score": "desc" },
    { "entityKey": "asc" }
  ]
}
```

#### 7-2-2. 단계 2: Dyn 조회 + Dyn 필터/정렬

- **index**: `ivm-products-dyn-{tenantId}` (read alias)
- **입력**: Static에서 얻은 _id/entityKey 목록(topN)
- **조회**: mget 또는 terms query
- **dyn filters**: sellable, in_stock, price range 등
- **dyn sort**: price_min, review_cnt, rating_avg, sales_30d 등

**Dyn DSL 샘플 (terms)**:

```json
{
  "size": 200,
  "_source": ["entityKey","sellable","in_stock","price_min","price_max","dc_price_min","discount_rate_max","review_cnt","rating_avg","sales_30d","updatedAt"],
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } },
        { "terms": { "entityKey": ["PRODUCT:oliveyoung:UA11279226","PRODUCT:oliveyoung:UA30953620"] } },
        { "term": { "sellable": true } },
        { "term": { "in_stock": true } },
        { "range": { "price_min": { "gte": 10000, "lte": 50000 } } }
      ]
    }
  }
}
```

#### 7-2-3. 단계 3: Merge 규칙 (응답 조립)

- **merge key**: entityKey (또는 _id 동일)
- **응답 필드**: title/brand/thumb → static, price/stock/reviews/ranking → dyn
- **dyn 누락 처리**: dyn 문서 없으면 sellable=false 또는 "unknown" 정책 SSOT화
- **운영 권장**: dyn 없으면 결과에서 제외(리스팅), 상세는 표시하되 상태 unknown

---

## 8. 정렬 규칙 SSOT

### 8-1. sort 파라미터 정의

| sort.key | Order | Source | Note |
|----------|-------|--------|------|
| relevance | desc | static _score | 검색어 있을 때 기본 |
| price | asc/desc | dyn price_min | |
| discount | desc | dyn discount_rate_max | |
| reviews | desc | dyn review_cnt | |
| rating | desc | dyn rating_avg | |
| sales30d | desc | dyn sales_30d | |
| rank | desc | dyn rank_score_base (또는 rank_score_24h) | 랭킹형 listing |

### 8-2. 결합 규칙 (권장)

| 상황 | 처리 |
|------|------|
| **q 존재** | static relevance로 후보 200~500 → dyn filter 적용 → 최종 정렬: relevance 유지(기본) 또는 user sort 요청 시 dyn sort로 재정렬 |
| **q 없음 (리스팅)** | static filter만 → dyn filter 적용 → dyn sort(가격/판매/리뷰 등) |

---

## 9. 인덱싱 파이프라인

### 9-1. Static 인덱싱 (카탈로그 생성)

1. RawData 수신
2. RuleSet → Slices
3. PRODUCT_SEARCH View 생성
4. Static Projection 생성 (검색/facet flatten 규칙 적용)
5. Static Bulk upsert to `...__write`
6. 검증 쿼리 세트 통과 시 read alias 유지(또는 swap)

### 9-2. Dynamic 인덱싱 (상태 업데이트)

1. 재고/가격/리뷰 이벤트 수신 (또는 배치 집계 결과 수신)
2. Dynamic Projection 생성 (필드 최소/정규화)
3. Dyn Bulk update(upsert) to `dyn...__write`
4. dynVersion/updatedAt로 역전 방지
5. 실패는 DLQ + 재처리 키

### 9-3. 동기화 키

- Static과 Dyn은 `_id`를 **완전히 동일하게** 강제
- entityKey 규칙이 SSOT이며, tenantId 포함 고정

---

## 10. Join 구현 옵션 (SSOT)

| 옵션 | 설명 | 상태 |
|------|------|------|
| **Option 1: 앱 레벨 join** | Static search → topN _id → Dyn mget/terms 조회 → merge | **SSOT. 2-인덱스 SOTA 기본** |
| **Option 2: terms lookup 서버사이드** | 특정 조건에서만 가능 | **비권장/실험**. 운영 제약 큼 |

**Search API**: Static-first 고정. 후보 topN=200~500 → Dyn terms 조회 후 dynFilters 적용. 정렬 기본 relevance, user sort 요청 시 dyn sort로 재정렬.

**Listing API**: 화면 유형별 분기.
- **필터형**(카테고리/속성/브랜드): Static-first. Static에서 필터로 후보 → Dyn 정렬/필터 적용. 랭킹/가격 정렬은 후보 기반(정확 topN 필요 시 후보 크게).
- **랭킹형**(베스트/신상/실시간): Dyn-first. Dyn에서 sellable/in_stock/price range + rank_score 정렬 → Static mget으로 merge. 필터 거의 없는 화면에 적용.

---

## 11. Reindex/Update Runbook

### 11-1. Static Reindex (분석기/동의어/매핑 변경 시)

1. 신규 static physical index 생성 (템플릿 적용)
2. backfill 인덱싱 (카탈로그 전량)
3. 검증 쿼리 세트 실행 (12절 상세)
   - Static Q01~Q20, Dyn D01~D20, Join 10개 (실데이터 치환 시 12-5절)
   - facet 결과 형태 확인 (brand/category/attrs)
4. read alias `ivm-products-{tenantId}`를 신규로 swap
5. write alias `...__write`도 신규로 swap
6. 구 인덱스는 보관 후 삭제

### 11-2. Dyn 업데이트 (고빈도)

1. 이벤트/배치 결과 수신
2. dynVersion 생성 (단조 증가)
3. Bulk update(upsert)
4. **역전 방지**: incoming dynVersion < stored dynVersion 이면 drop
5. 실패는 DLQ 적재 후 재처리

### 11-3. Dyn Reindex (드묾)

- **발생 시점**: Dyn 매핑 변경 시에만 수행
- **특징**: 보통 Dyn은 필드 추가 정도면 새 인덱스 생성 + alias swap으로 처리
- **절차**: Static reindex와 동일 (신규 물리 인덱스 → backfill → 검증 → alias swap)

### 11-4. Backfill 인덱싱 갭 및 해결 방안 (중요)

**현재 갭**: 인덱싱은 **메시지(SinkEvent) 페이로드만** 처리한다. 이벤트 드리븐 구조라 실시간 ingest 시에만 SinkEvent가 생성되고, Lambda가 이를 처리해 OpenSearch에 인덱싱한다. **전체 데이터를 벌크로 인덱싱할 경로가 없다.**

| 구분 | 실시간 인덱싱 | Bulk Reindex |
|------|----------------|--------------|
| **트리거** | RawData Ingest → SinkEvent | 메시지 없음 |
| **데이터 소스** | IngestionWorkflow 결과(View) | ??? |
| **현재 상태** | ✅ 구현됨 | ❌ 미구현 |

**해결 방안**:

1. **BackfillType.VIEW_TO_SINK 구현** (권장)
   - `BackfillType`에 이미 `VIEW_TO_SINK` 정의됨. 구현만 추가.
   - 흐름: `entityKey 목록` → `RawData.getLatest` → `version` → `Slice.getByVersion` → `View 조합(QueryViewWorkflow/JoinExecutor)` → `SinkPlugin.batch()` 호출
   - View는 저장되지 않으므로 Slice를 병합해 실시간 생성

2. **FULL_REPROCESS 확장**
   - 현재 `FULL_REPROCESS`는 RawData → Slice까지만 수행. Sink 단계 추가.
   - Slice 생성 후 동일 entityKey에 대해 View 조합 → Sink 전송

3. **구현 시 참고**
   - `QueryViewWorkflow`: Slice → View 조합 로직
   - `SliceRepositoryPort.getByVersion`, `getLatestVersion`
   - `OpenSearchSinkPlugin`: `SinkPayload`(viewData) 형태로 bulk 전송

**Runbook 11-1 절차 보완**:

- 11-1 2단계 "backfill 인덱싱 (카탈로그 전량)" 수행 전 `VIEW_TO_SINK` Backfill 타입이 구현되어 있어야 함.
- **구현 계획**: [view-to-sink-backfill-plan.md](./view-to-sink-backfill-plan.md) 참고.

---

## 12. 검증 쿼리 세트 v1

### 12-1. Static 검증 쿼리 세트 (20개)

#### 12-1-1. 목적

Static 인덱스의 (1) 검색 결과 생성 (2) 필터 정확성 (3) facet 집계 안정성 (4) analyzer 동작을 alias swap 전에 검증하는 세트. 각 케이스는 "최소 기대조건"만 체크(결과 품질 평가는 Phase 3에서 별도).

#### 12-1-2. 공통 파라미터

| 항목 | 값 |
|------|-----|
| tenantId | oliveyoung |
| Static index alias | ivm-products-{tenantId} |
| size | 40 |
| track_total_hits | true |
| _source | 표시용 최소만 |

#### 12-1-3. 케이스 목록

**한글 일반 검색 (5)**

| ID | query | 기대 |
|----|-------|------|
| Q01 | q="선크림" | total_hits > 0, _score 존재, facet(brand/category/attrs) 응답 존재 |
| Q02 | q="비건 선크림" | total_hits > 0, badge facet에 vegan 관련 bucket 존재 가능(없어도 에러 없어야 함) |
| Q03 | q="라운드랩 토너" | brand facet 상위 bucket에 특정 brand_code 반복 등장 |
| Q04 | q="시카 크림" | attrs facet 비어도 OK, 결과 0이면 analyzer/키워드 매핑 확인 필요 |
| Q05 | q="클린 뷰티" | total_hits > 0 또는 search_keywords 매핑/동의어 필요 플래그 |

**영문/혼합 검색 (3)**

| ID | query | 기대 |
|----|-------|------|
| Q06 | q="COSRX" | total_hits > 0, brand_en 또는 search_keywords에 매칭 |
| Q07 | q="cleansing oil" | total_hits >= 0, 0이면 title_en/brand_en 채움 여부 확인 |
| Q08 | q="비타민C serum" | total_hits >= 0, 0이면 search_keywords 정규화 규칙 확인 |

**카테고리/속성/뱃지 필터 (8)**

| ID | filter | 기대 |
|----|--------|------|
| Q09 | category_display=["100000200"] | total_hits >= 0, category agg bucket에 동일 id 포함 |
| Q10 | category_display=["100000200","100000201"] | total_hits >= 0, terms filter 동작 확인 |
| Q11 | category_std=["L:10","M:101"] | total_hits >= 0, category_std가 keyword[]로 동작 |
| Q12 | brand_code=["BR12345"] | 결과 0이면 데이터 문제 가능, 쿼리 오류 없어야 함 |
| Q13 | badge_vegan=true | 결과 문서 모두 badge_vegan=true, agg 정상 |
| Q14 | badge_clean=true | 동일 |
| Q15 | attr_codes=["FORM_GEL"] | attr_codes가 keyword[]로 필터 동작 |
| Q16 | attr_kv=["SKIN=sensitive"] | normalizeValue 규칙대로 들어간 kv로만 필터 |

**옵션 텍스트 (2)**

| ID | query | 기대 |
|----|-------|------|
| Q17 | q="리필" | options.name이 검색 대상에 없으면 0일 수 있음. options.name을 search_fields에 포함할지 정책 결정 유도 |
| Q18 | nested term options.sku="8801234567890" | nested 구조/쿼리 오류 없이 동작 |

**Facet 안정성 (2)**

| ID | query | 기대 |
|----|-------|------|
| Q19 | match_all + filter tenantId, aggs: brand/category/attrs | aggs 반환 구조 항상 동일(빈 bucket이어도 OK) |
| Q20 | filter: category_display + badge_vegan | facet이 filter context 기준으로 축소 |

#### 12-1-4. Static DSL 템플릿 (검증용)

**템플릿 A: 검색어 있음**

```json
{
  "size": 40,
  "track_total_hits": true,
  "_source": ["entityKey","uaCode","title_ko","brand_code","brand_ko","thumb_url","category_display","attr_codes","attr_formulation","attr_skin_type","attr_main_functions","attr_ingredients","badge_vegan","badge_clean","badge_cruelty_free"],
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "__Q__",
            "fields": ["title_ko^3","brand_ko^2","search_keywords","title_en","brand_en"],
            "type": "best_fields",
            "operator": "and"
          }
        }
      ],
      "filter": [
        { "term": { "tenantId": "oliveyoung" } }
      ]
    }
  },
  "aggs": {
    "brand": { "terms": { "field": "brand_code", "size": 50 } },
    "category": { "terms": { "field": "category_display", "size": 50 } },
    "formulation": { "terms": { "field": "attr_formulation", "size": 30 } },
    "skin_type": { "terms": { "field": "attr_skin_type", "size": 30 } },
    "main_functions": { "terms": { "field": "attr_main_functions", "size": 50 } },
    "ingredients": { "terms": { "field": "attr_ingredients", "size": 50 } }
  },
  "sort": [
    { "_score": "desc" },
    { "entityKey": "asc" }
  ]
}
```

**템플릿 B: 검색어 없음 (리스팅/필터)**

```json
{
  "size": 40,
  "track_total_hits": true,
  "_source": ["entityKey","uaCode","title_ko","brand_code","brand_ko","thumb_url","category_display","attr_codes","attr_formulation","attr_skin_type","attr_main_functions","attr_ingredients","badge_vegan","badge_clean","badge_cruelty_free"],
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } }
      ]
    }
  },
  "aggs": {
    "brand": { "terms": { "field": "brand_code", "size": 50 } },
    "category": { "terms": { "field": "category_display", "size": 50 } },
    "formulation": { "terms": { "field": "attr_formulation", "size": 30 } },
    "skin_type": { "terms": { "field": "attr_skin_type", "size": 30 } },
    "main_functions": { "terms": { "field": "attr_main_functions", "size": 50 } },
    "ingredients": { "terms": { "field": "attr_ingredients", "size": 50 } }
  },
  "sort": [
    { "updatedAt": "desc" },
    { "entityKey": "asc" }
  ]
}
```

---

### 12-2. Dynamic 검증 쿼리 세트 (20개)

#### 12-2-1. 목적

Dyn 인덱스의 (1) 업데이트 반영 (2) 역전 방지(dynVersion) (3) 가격/재고 필터 및 정렬을 검증하는 세트.

#### 12-2-2. 케이스 목록

**기본 상태 (5)**

| ID | 설명 | 기대 |
|----|------|------|
| D01 | entityKey 2개로 terms 조회 | 필드 존재(sellable/in_stock/price_min/review_cnt 등) |
| D02 | sellable=true 필터 | 결과 모두 sellable=true |
| D03 | in_stock=true 필터 | 결과 모두 in_stock=true |
| D04 | sellable + in_stock 조합 | bool filter 정상 동작 |
| D05 | entityKey 목록 중 1개는 없는 값 포함 | 누락은 반환 안 됨(merge 정책으로 처리) |

**가격 (6)**

| ID | 설명 | 기대 |
|----|------|------|
| D06 | price_min range (gte) | |
| D07 | price_min range (lte) | |
| D08 | price_min between | |
| D09 | discount_rate_max desc sort | |
| D10 | price_min asc sort | |
| D11 | dc_price_min exists filter (할인가 있는 상품) | |

**리뷰/랭킹 (4)**

| ID | 설명 | 기대 |
|----|------|------|
| D12 | review_cnt desc sort | |
| D13 | rating_avg desc sort | |
| D14 | sales_30d desc sort | |
| D15 | rank_score_base desc sort | |

**역전 방지 (dynVersion) (3)**

| ID | 설명 | 기대 |
|----|------|------|
| D16 | 동일 entityKey에 dynVersion 증가 업데이트 후 조회 | 최신 값만 노출 |
| D17 | 낮은 dynVersion으로 update 시도 (테스트 환경) | drop 정책(앱) 또는 OS external version으로 거부 |
| D18 | updatedAt 역전 시나리오 | dynVersion 우선 |

**옵션 정밀 (2, 선택)**

| ID | 설명 | 기대 |
|----|------|------|
| D19 | dyn.options nested price filter | |
| D20 | dyn.options nested in_stock filter | |

#### 12-2-3. Dyn DSL 템플릿 (검증용)

**템플릿 C: Dyn 필터 + 정렬**

```json
{
  "size": 40,
  "track_total_hits": true,
  "_source": ["entityKey","sellable","in_stock","price_min","price_max","dc_price_min","discount_rate_max","review_cnt","rating_avg","sales_30d","rank_score_base","dynVersion","updatedAt"],
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } },
        { "term": { "sellable": true } },
        { "term": { "in_stock": true } }
      ]
    }
  },
  "sort": [
    { "price_min": "asc" },
    { "entityKey": "asc" }
  ]
}
```

**템플릿 D: 특정 entityKey 목록 조회**

```json
{
  "size": 200,
  "_source": ["entityKey","sellable","in_stock","price_min","dc_price_min","review_cnt","rating_avg","sales_30d","dynVersion","updatedAt"],
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } },
        { "terms": { "entityKey": ["__K1__","__K2__","__K3__"] } }
      ]
    }
  }
}
```

---

### 12-3. Join 검증 세트 (10개)

#### 12-3-1. 목적

Static 후보 + Dyn merge 후 "필터/정렬이 요구대로 동작"하는지 검증.

#### 12-3-2. 케이스

| # | 시나리오 | 기대 |
|---|----------|------|
| 1 | q=선크림, dynFilters in_stock=true | 결과 모두 in_stock=true |
| 2 | q=선크림, sort=price asc | 가격 오름차순 |
| 3 | category_display=... listing, sellable=true | 결과 모두 sellable=true |
| 4 | brand filter + sort=reviews desc | |
| 5 | badge_vegan=true + price range | |
| 6 | attr_codes 필터 + sort=sales_30d desc | |
| 7 | dyn 누락 문서가 후보에 섞였을 때 | 결과 제외/unknown 정책 적용 |
| 8 | facet | static 기준으로만 계산(동작 확인) |
| 9 | page/search_after | 중복/누락 없는지 |
| 10 | exact lookup | static+dyn 합쳐서 단일 응답 완성 |

---

### 12-4. 검증 합격 기준 (DoD)

#### 12-4-1. Static

- Q01~Q20에서 쿼리 오류 0
- facet 응답 구조 항상 동일
- category_display/attr_codes 필터가 "타입 충돌 없이" 동작

#### 12-4-2. Dyn

- D01~D20에서 쿼리 오류 0
- sort 필드 기준 정렬이 일관
- dynVersion 역전 방지 정책이 테스트로 증명됨

#### 12-4-3. Join

- Join 10개 케이스에서 필터/정렬/페이지네이션이 요구대로 동작

---

### 12-5. 실데이터 기반 검증 세트 v1.1 생성 절차

#### 12-5-1. 목적

Q01~Q20, D01~D20, Join 10개를 "샘플 문자열"이 아니라 **실제 운영 코드(brand/category/attr)**로 고정해 alias swap 전 검증을 자동화.

#### 12-5-2. 원칙

- 샘플 값은 **집계 상위 N**에서 자동 선택
- 값이 없는 경우(0 bucket)는 "해당 케이스 skip"이 아니라 **"데이터/파이프라인 결함"**으로 플래그
- 결과의 결정성을 위해 모든 리스트는 **정렬 후 사용**

#### 12-5-3. Static 샘플 값 추출 DSL

**브랜드/카테고리/속성 top bucket 추출**

```json
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } }
      ]
    }
  },
  "aggs": {
    "brand_top": { "terms": { "field": "brand_code", "size": 20 } },
    "cat_display_top": { "terms": { "field": "category_display", "size": 50 } },
    "attr_codes_top": { "terms": { "field": "attr_codes", "size": 50 } }
  }
}
```

**추출 규칙**

| 변수 | 규칙 | 결측 시 |
|------|------|---------|
| brandCodes | brand_top.buckets[0..2].key (3개) | brand_top empty → 브랜드 projection 결함 플래그 |
| categoryDisplay | cat_display_top.buckets[0..4].key (5개) | empty → CATEGORY slice 또는 flatten 결함 플래그 |
| attrCodes | attr_codes_top.buckets[0..4].key (5개) | empty → INDEX slice 또는 attr flatten 결함 플래그 |

**attr_kv 별도 집계**

```json
{
  "size": 0,
  "query": { "bool": { "filter": [{ "term": { "tenantId": "oliveyoung" } }] } },
  "aggs": {
    "attr_kv_top": { "terms": { "field": "attr_kv", "size": 50 } }
  }
}
```

- attrKv = attr_kv_top.buckets[0].key

#### 12-5-4. Dyn 샘플 값 추출 DSL

**entityKey pool 구성 (sellable=true, in_stock=true 상위 200)**

```json
{
  "size": 200,
  "_source": ["entityKey","price_min","review_cnt","rating_avg","sales_30d","rank_score_base","dynVersion","updatedAt"],
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenantId": "oliveyoung" } },
        { "term": { "sellable": true } },
        { "term": { "in_stock": true } }
      ]
    }
  },
  "sort": [
    { "updatedAt": "desc" },
    { "entityKey": "asc" }
  ]
}
```

**추출 규칙**

| 변수 | 규칙 |
|------|------|
| entityKeyPool | hits.hits[0..199]._source.entityKey |
| Dyn terms 조회 | pool에서 2~10개씩 뽑아 사용 |
| priceMinSamples | sort(unique(price_min))[p10, p50, p90] 개념으로 3개 구간 (근사 OK) |

#### 12-5-5. 치환 규칙 (실데이터 코드)

| 케이스 | 필드 | 치환 값 |
|--------|------|---------|
| Q09 | category_display | categoryDisplay[0] |
| Q10 | category_display | categoryDisplay[0..1] |
| Q11 | category_std | 운영 데이터에서 large/medium 코드 확실한 경우만 (표준 카테고리 top bucket 별도 집계 권장) |
| Q12 | brand_code | brandCodes[0] |
| Q15 | attr_codes | attrCodes[0] |
| Q16 | attr_kv | attrKv[0] |
| Dyn price range | price_min | entityKeyPool에서 price_min 분포 기반 구간 생성 |

---

### 12-6. search_after 페이징 SSOT

#### 12-6-1. 왜 from/size가 아닌가

- from/size: deep paging 비용 증가 + 결과 흔들림 위험
- Dyn 정렬(가격/리뷰/판매량)은 업데이트가 잦아 from/size 기반 페이징이 깨지기 쉬움
- **search_after**: 정렬 키가 결정적이면 중복/누락을 가장 안정적으로 방지

#### 12-6-2. 정렬 키 규칙 (필수)

- 어떤 정렬이든 마지막 **tie-breaker**는 반드시 "유일하고 결정적인 값"
- 권장 tie-breaker: **entityKey** (keyword, 유일, 결정적)

#### 12-6-3. Static 페이징 정렬

| 상황 | sort | search_after |
|------|------|--------------|
| q 있음 (검색) | ["_score desc", "entityKey asc"] | [last._score, last.entityKey] |
| q 없음 (리스팅) | ["updatedAt desc", "entityKey asc"] | [last.updatedAt, last.entityKey] |

**주의**: _score는 부동소수점이라 경계에서 드물게 흔들릴 수 있으나, tie-breaker가 entityKey라 중복/누락은 대부분 방지. 더 강하게 잠그려면 "정렬용 정수 rank 필드"를 Static에 추가(선택).

#### 12-6-4. Dyn 페이징 정렬

| 상황 | sort | search_after |
|------|------|--------------|
| 가격 정렬 | ["price_min asc", "entityKey asc"] | [last.price_min, last.entityKey] |
| 리뷰수 정렬 | ["review_cnt desc", "entityKey asc"] | [last.review_cnt, last.entityKey] |
| 판매량 정렬 | ["sales_30d desc", "entityKey asc"] | [last.sales_30d, last.entityKey] |
| 최신 상태 | ["updatedAt desc", "entityKey asc"] | [last.updatedAt, last.entityKey] |

#### 12-6-5. Dyn 페이징 안전장치 (필수)

| 항목 | 내용 |
|------|------|
| **스냅샷 시점 고정** | Dyn에 snapshotEpoch/asOf 추가 가능하면 최선. 불가하면 "요청 최초 페이지에서 asOf=now 잡고, 다음 페이지에도 동일 asOf 전달"하는 앱 레벨 정책 |
| **페이지 단위 merge** | Static 후보를 페이지보다 크게 뽑아 Dyn 필터 후 40개 채우는 방식 가능. 단 페이징 SSOT가 복잡해지므로 아래 A/B 중 하나로 고정 권장 |

---

### 12-7. Join 페이징 SSOT (운영형 2안)

| 안 | 용도 | 흐름 | 장점 |
|----|------|------|------|
| **A안: Dyn-first listing** | 랭킹형(베스트/신상/실시간) | Dyn에서 필터+정렬+search_after로 entityKey page(예: 40개) → Static terms/mget으로 merge. nextSearchAfter는 Dyn 그대로 반환 | 고빈도 필드 기반 정렬/페이징 완전 안정화 |
| **B안: Static-first listing** | 필터형(카테고리/속성/브랜드) | Static에서 필터로 후보 → Dyn terms 조회 후 dynFilters 적용. 정렬은 relevance 또는 dyn sort | facet/필터 정확도 유지 |

---

### 12-8. 검증 자동화 실행 플로우 (최종)

#### 12-8-1. 실행 순서 (Strict chronological)

1. Static 샘플 벡터 추출 (brand/category/attr/attr_kv)
2. Dyn 샘플 entityKeyPool 추출
3. Static Q01~Q20 실행 (치환된 실데이터 값 사용)
4. Dyn D01~D20 실행 (치환된 실데이터 값 사용)
5. Join 10개 실행
6. 결과 저장 (스냅샷)
7. alias swap 전후로 동일 세트 실행 → diff 비교

#### 12-8-2. 합격 기준 (강화판)

- 쿼리 오류 0
- 페이징 3페이지 연속 호출 시 **중복 0, 누락 0** (동일 asOf 기준)
- Static facet 응답 키 구조 불변
- Dyn 정렬 키가 null인 문서 처리 정책 일관 (예: null은 뒤로)

---

### 12-9. 테스트 값 생성 규칙 요약 (런북용)

#### 12-9-1. 생성

| 변수 | 소스 |
|------|------|
| brandCodes | static terms agg top3 |
| categoryDisplay | static terms agg top5 |
| attrCodes | static terms agg top5 |
| attrKv | static terms agg top1 |
| entityKeyPool | dyn 최신순 top200 (sellable=true, in_stock=true) |

#### 12-9-2. 치환

| 케이스 | 치환 |
|--------|------|
| Q09 | categoryDisplay[0] |
| Q10 | categoryDisplay[0..1] |
| Q12 | brandCodes[0] |
| Q15 | attrCodes[0] |
| Q16 | attrKv[0] |
| Dyn price range | entityKeyPool에서 price_min 분포 기반 구간 생성 |

---

## 13. 단계별 작업계획 (실행용)

### Phase 0 (D0~D2): SSOT 확정 및 템플릿 생성

| 작업 | 산출물 |
|------|--------|
| Index SSOT v1 확정 (static/dyn 네이밍, alias, id 규칙) | |
| Static/Dyn index template JSON 작성 및 저장소에 계약으로 고정 | `index-template-static.v1.json`, `index-template-dyn.v1.json` |
| Projection SSOT v1 작성 (필드 생성 규칙, flatten 규칙, null/empty 규칙) | `projection-product-search-to-static.v1.md`, `projection-dyn.v1.md` |

### Phase 1 (D3~D6): 인덱싱 파이프라인 구현

| 작업 | 산출물 |
|------|--------|
| Static Sink: bulk upsert + retry + DLQ | Static/Dyn Sink 구현 |
| Dyn Sink: bulk update(upsert) + dynVersion 역전 방지 | DLQ 재처리 커맨드/잡 |
| 샘플 데이터로 1만/10만 단위 적재 테스트 | |
| 최소 모니터링 지표 (실패율/지연/재시도) 로그 포맷 고정 | |

### Phase 2 (D7~D10): 리트리빙 API 구현 (4 템플릿)

| 작업 | 산출물 |
|------|--------|
| Search API: Static 쿼리 생성기 + Dyn merge 적용 | `query-template-v1.md` |
| Listing API: Static 후보 + Dyn 필터/정렬 지원 | DSL 생성 코드(서버) |
| Exact API: uaCode/entityKey | |
| Facet API: Static facet 기반 (브랜드/카테고리/속성) | |

### Phase 3 (D11~D15): 검색 품질 SOTA 세팅

| 작업 | 산출물 |
|------|--------|
| nori user_dict/동의어 사전 버전관리 (배포 절차 포함) | `synonyms-ko.v1.txt`, `userdict-ko.v1.txt` |
| 부스팅 룰 SSOT (브랜드/카테고리/키워드) | `boosting-rule.v1.md` |
| "속성 필터 정확도"를 attr_codes/attr_kv로 고정 | |
| 랭킹 결합 룰 (Static score vs Dyn metrics) 결정 | `ranking-merge.v1.md` |

### Phase 4 (상시): 운영 표준화

| 작업 | 산출물 |
|------|--------|
| Static reindex runbook 고정 (blue/green + alias swap) | `runbook-reindex.v1.md` |
| Dyn 업데이트 레이트 제한/배치 flush 표준화 | `runbook-dyn-update.v1.md` |
| 장애 시 "Dyn만 degrade(미적용)" fallback 전략 고정 | `fallback-policy.v1.md` |

---

## 14. 적용 체크리스트 (기존 문서 대비 델타)

### 14-1. 필수 변경

| 항목 | 내용 |
|------|------|
| attributes[].attrValue 기반 집계/필터 | **금지**, attr_kv/attr_codes로 고정 |
| displayCategories nested 집계 | **금지**, category_display flatten으로 고정 |
| 재고/가격/리뷰/랭킹 | Static에서 **제거**, Dyn으로 이동 |
| dynamic mapping | **폐기**, static/dyn 템플릿에서 dynamic=false, 필수 필드 타입 고정 |

### 14-2. 권장 변경

| 항목 | 내용 |
|------|------|
| Static options | sku/name만 유지, 가격/재고는 dyn.options로 이동 |
| dyn.options | 정밀 옵션 필터 필요 시에만 사용 |
| option_stock_summary | **flatten 완료**: option_count, option_in_stock_count (integer) |

---

## 15. 환경 변수

| 변수 | 설명 | 예시 |
|------|------|------|
| OPENSEARCH_ENDPOINT | OpenSearch 엔드포인트 | https://search-xxx.ap-northeast-2.es.amazonaws.com |
| OPENSEARCH_STATIC_READ_ALIAS | Static read alias | ivm-products-{tenantId} |
| OPENSEARCH_STATIC_WRITE_ALIAS | Static write alias | ivm-products-{tenantId}__write |
| OPENSEARCH_STATIC_PHYSICAL_PATTERN | Static 물리 인덱스 패턴 | ivm-products-static-{tenantId}-v{schemaVersion}-{stamp} |
| OPENSEARCH_DYN_READ_ALIAS | Dyn read alias | ivm-products-dyn-{tenantId} |
| OPENSEARCH_DYN_WRITE_ALIAS | Dyn write alias | ivm-products-dyn-{tenantId}__write |
| OPENSEARCH_DYN_PHYSICAL_PATTERN | Dyn 물리 인덱스 패턴 | ivm-products-dyn-{tenantId}-v{schemaVersion}-{stamp} |
| OPENSEARCH_USERNAME | Basic 인증 사용자 | (선택) |
| OPENSEARCH_PASSWORD | Basic 인증 비밀번호 | (선택) |

---

## 16. 참고

- [index-template-static.v1.json](opensearch/index-template-static.v1.json) - Static 인덱스 템플릿 (nori 플러그인 필요)
- [index-template-dyn.v1.json](opensearch/index-template-dyn.v1.json) - Dynamic 인덱스 템플릿
- [sinkrule-opensearch-product.v1.yaml](../../src/main/resources/contracts/v1/sinkrule-opensearch-product.v1.yaml) - SinkRule 계약
- [view-product-search.v1.yaml](../../src/main/resources/contracts/v1/view-product-search.v1.yaml) - PRODUCT_SEARCH View
- [ruleset-product-oliveyoung.v1.yaml](../../src/main/resources/contracts/v1/ruleset-product-oliveyoung.v1.yaml) - Slice 정의
- [product-schema-dx-proposal.md](../rfc/product-schema-dx-proposal.md) - RFC 상세
