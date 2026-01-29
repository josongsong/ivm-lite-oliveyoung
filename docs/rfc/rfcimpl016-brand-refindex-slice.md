# RFC-IMPL-016: Brand RefIndexSlice 패턴 구현

| 항목 | 내용 |
|------|------|
| **상태** | Draft |
| **작성일** | 2026-01-29 |
| **작성자** | IVM-Lite Team |
| **관련 RFC** | RFC-006 (RefIndexSlice 패턴), RFC-001 (슬라이싱 원칙) |
| **예상 공수** | BE: 2일, Contract: 0.5일 |
| **우선순위** | P0 - Critical (Brand 슬라이싱 불가 상태) |

---

## 1. Executive Summary

현재 `entity-brand.v1.yaml`이 `ruleset.brand.v1`을 참조하지만 **해당 RuleSet이 존재하지 않아 Brand 데이터 슬라이싱이 불가능**합니다.

RFC-006의 **RefIndexSlice 패턴**을 적용하여:
- Brand 변경 시 **전체 Product 재슬라이싱 방지** (효율성)
- **Brand SUMMARY → Product ENRICHED** 연결로 영향 범위 최소화
- View에서 **CORE + ENRICHED 조합**으로 Brand 정보 제공

---

## 2. 현재 문제 분석

### 2.1 누락된 구성요소

| 구성요소 | 상태 | 문제 |
|----------|------|------|
| `entity-brand.v1.yaml` | ✅ 존재 | `ruleset.brand.v1` 참조 중 |
| `ruleset.brand.v1.yaml` | ❌ **누락** | Brand 슬라이싱 불가 |
| Product `indexes.references: BRAND` | ✅ 존재 | Fanout 트리거 대상 없음 |
| Product CORE `joins.brandInfo` | ✅ 존재 | Brand Slice 없어서 JOIN 불가 |

### 2.2 현재 데이터 흐름 (broken)

```
Brand RawData ──→ ❌ 슬라이싱 안됨 (RuleSet 없음)
                      │
                      ↓ Fanout 트리거 안됨
                      │
Product RawData ──→ Product CORE (Brand 정보 없음) ❌
```

---

## 3. 해결 방안: RefIndexSlice 패턴

### 3.1 목표 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                        Brand 변경 시                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Brand RawData                                                   │
│       │                                                          │
│       ├──→ Brand CORE Slice (전체 데이터)                        │
│       │                                                          │
│       └──→ Brand SUMMARY Slice (REF_INDEX)                       │
│                 │   - brandId, name, logoUrl만                   │
│                 │   - 경량 참조용                                 │
│                 │                                                │
│                 ↓ Fanout (EntityUpdated)                         │
│                 │                                                │
│  ┌─────────────────────────────────────┐                        │
│  │ Product ENRICHED Slice 재빌드       │                        │
│  │ (SUMMARY 참조, CORE는 그대로 유지)   │                        │
│  └─────────────────────────────────────┘                        │
│                 │                                                │
│                 ↓                                                │
│  ┌─────────────────────────────────────┐                        │
│  │ View = CORE + ENRICHED 조합         │                        │
│  │ (Brand 이름/로고 포함)               │                        │
│  └─────────────────────────────────────┘                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 효율성 비교

| 방식 | Brand 변경 시 재빌드 | 비용 |
|------|---------------------|------|
| **Option A (단순)** | 연관 Product CORE 전체 | 수천~수만 건 |
| **Option B (RefIndex)** | Product ENRICHED만 | SUMMARY 변경분만 |

**핵심**: Brand 이름/로고만 바뀌면 SUMMARY만 변경 → ENRICHED만 재빌드

---

## 4. Contract 설계

### 4.1 Brand RuleSet (`ruleset.brand.v1.yaml`)

```yaml
kind: RULESET
id: ruleset.brand.v1
version: 1.0.0
status: ACTIVE

entityType: BRAND

# 변경 경로 → 영향 슬라이스
impactMap:
  CORE:
    - "/brandId"
    - "/name"
    - "/logoUrl"
    - "/description"
    - "/country"
    - "/website"
  SUMMARY:
    - "/brandId"
    - "/name"
    - "/logoUrl"

joins: []

slices:
  # 1. CORE: 전체 Brand 데이터 (상세 페이지용)
  - type: CORE
    buildRules:
      type: PassThrough
      fields:
        - "*"

  # 2. SUMMARY: 경량 참조용 (REF_INDEX)
  #    - Product ENRICHED에서 참조
  #    - name, logoUrl만 포함 (고빈도 변경 필드 최소화)
  - type: SUMMARY
    sliceKind: REF_INDEX
    buildRules:
      type: MapFields
      mappings:
        - from: brandId
          to: id
        - from: name
          to: name
        - from: logoUrl
          to: logoUrl

# Brand 변경 시 영향받는 엔티티 없음 (Product가 Brand를 참조)
indexes: []
```

### 4.2 Product RuleSet 수정 (`ruleset-product-doc001.v1.yaml`)

```yaml
# 기존 슬라이스 유지 + ENRICHED 추가

slices:
  # ... 기존 CORE, PRICE, INVENTORY, MEDIA, CATEGORY, INDEX 유지 ...

  # 추가: ENRICHED 슬라이스 (Brand 정보 포함)
  - type: ENRICHED
    sliceKind: ENRICHMENT
    buildRules:
      type: PassThrough
      fields: []  # 자체 필드 없음, JOIN 결과만 포함
    joins:
      - name: brand
        type: LOOKUP
        sourceFieldPath: $.masterInfo.brand.code
        targetEntityType: BRAND
        targetSliceType: SUMMARY  # REF_INDEX 참조
        targetKeyPattern: "BRAND#{tenantId}#{value}"
        required: false
        missingPolicy: PARTIAL_ALLOWED
        projection:
          mode: COPY_FIELDS
          fields:
            - from: name
              to: brandName
            - from: logoUrl
              to: brandLogoUrl
```

### 4.3 impactMap 수정

```yaml
impactMap:
  # ... 기존 유지 ...
  ENRICHED:
    - "/masterInfo/brand/code"
    # Brand SUMMARY 변경 시 Fanout으로 트리거됨
```

### 4.4 View 수정 (`view-product-detail.v1.yaml`)

```yaml
kind: VIEW_DEFINITION
id: view.product.detail.v1
version: 1.1.0  # 버전 업
status: ACTIVE

viewName: PRODUCT_DETAIL
entityType: PRODUCT
description: "상품 상세 - 전체 정보 조합 (Brand 정보 포함)"

requiredSlices:
  - CORE
  - PRICE
  - MEDIA
  - ENRICHED  # 추가: Brand 정보

optionalSlices:
  - INVENTORY
  - CATEGORY
  - INDEX

# ... 나머지 동일 ...
```

---

## 5. Fanout 메커니즘

### 5.1 Index 정의 (Product RuleSet)

```yaml
indexes:
  - type: brand
    selector: $.masterInfo.brand.code
    references: BRAND        # Brand → Product 역참조
    maxFanout: 10000
    triggerSlices:           # 추가: 영향받는 슬라이스 명시
      - ENRICHED
```

### 5.2 Fanout 흐름

```
1. Brand RawData 변경
      │
      ↓
2. Brand SUMMARY Slice 재빌드
      │
      ↓
3. Outbox: EntityUpdated(BRAND, brandId)
      │
      ↓
4. InvertedIndex 조회: brand index에서 연관 Product 목록
      │
      ↓
5. 각 Product에 대해:
   - ENRICHED Slice만 재빌드 (CORE 유지)
   - View 무효화
```

---

## 6. 구현 계획

### Phase 1: Contract 생성 (Day 1 - 오전)

| Task | 파일 | 설명 |
|------|------|------|
| 1.1 | `ruleset.brand.v1.yaml` | Brand RuleSet 신규 생성 |
| 1.2 | `ruleset-product-doc001.v1.yaml` | ENRICHED 슬라이스 추가 |
| 1.3 | `view-product-detail.v1.yaml` | ENRICHED 포함하도록 수정 |
| 1.4 | Contract 검증 | `./gradlew validateContracts` |

### Phase 2: 도메인 확장 (Day 1 - 오후)

| Task | 파일 | 설명 |
|------|------|------|
| 2.1 | `SliceType.kt` | `SUMMARY`, `ENRICHED` 추가 |
| 2.2 | `SliceKind.kt` | `REF_INDEX`, `ENRICHMENT` enum 추가 |
| 2.3 | `RuleSetContract.kt` | `sliceKind` 파싱 추가 |

### Phase 3: SlicingEngine 확장 (Day 2 - 오전)

| Task | 파일 | 설명 |
|------|------|------|
| 3.1 | `SlicingEngine.kt` | SUMMARY 슬라이스 빌드 로직 |
| 3.2 | `SlicingEngine.kt` | ENRICHED 슬라이스 빌드 (JOIN 포함) |
| 3.3 | `JoinExecutor.kt` | REF_INDEX 참조 로직 |

### Phase 4: Fanout 연동 (Day 2 - 오후)

| Task | 파일 | 설명 |
|------|------|------|
| 4.1 | `OutboxPollingWorker.kt` | ENRICHED 재빌드 트리거 |
| 4.2 | `SlicingWorkflow.kt` | `triggerSlices` 필터링 |
| 4.3 | 통합 테스트 | Brand → Product Fanout |

---

## 7. 테스트 계획

### 7.1 단위 테스트

```kotlin
// SliceTypeTest.kt
@Test
fun `SUMMARY 슬라이스는 REF_INDEX 종류여야 함`() {
    val ruleSet = loadContract("ruleset.brand.v1")
    val summarySlice = ruleSet.slices.find { it.type == SliceType.SUMMARY }

    assertThat(summarySlice?.sliceKind).isEqualTo(SliceKind.REF_INDEX)
}

// SlicingEngineTest.kt
@Test
fun `Brand SUMMARY는 id, name, logoUrl만 포함해야 함`() {
    val rawData = createBrandRawData(
        brandId = "BR001",
        name = "테스트브랜드",
        logoUrl = "https://...",
        description = "설명",  // SUMMARY에 포함 안됨
        website = "https://..."  // SUMMARY에 포함 안됨
    )

    val summary = slicingEngine.slice(rawData, SliceType.SUMMARY)

    assertThat(summary.payload.keys).containsExactly("id", "name", "logoUrl")
}
```

### 7.2 통합 테스트

```kotlin
// BrandFanoutIntegrationTest.kt
@Test
@IntegrationTag
fun `Brand SUMMARY 변경 시 연관 Product ENRICHED만 재빌드`() {
    // Given: Product가 Brand를 참조
    val brand = ingestBrand("BR001", name = "올리브영")
    val product = ingestProduct("P001", brandCode = "BR001")

    // When: Brand 이름 변경
    updateBrand("BR001", name = "올리브영 신규")

    // Then: Product ENRICHED만 재빌드, CORE는 유지
    val productSlices = getProductSlices("P001")
    assertThat(productSlices.enriched.brandName).isEqualTo("올리브영 신규")
    assertThat(productSlices.core.version).isEqualTo(1)  // CORE 버전 유지
    assertThat(productSlices.enriched.version).isEqualTo(2)  // ENRICHED만 버전 증가
}
```

---

## 8. 롤백 계획

### 8.1 Contract 롤백

```bash
# 문제 발생 시 이전 버전으로 복원
git checkout HEAD~1 -- src/main/resources/contracts/v1/ruleset.brand.v1.yaml
git checkout HEAD~1 -- src/main/resources/contracts/v1/ruleset-product-doc001.v1.yaml
git checkout HEAD~1 -- src/main/resources/contracts/v1/view-product-detail.v1.yaml
```

### 8.2 데이터 정리

```sql
-- Brand SUMMARY, Product ENRICHED 슬라이스 삭제
DELETE FROM slices WHERE slice_type IN ('SUMMARY', 'ENRICHED');

-- 재슬라이싱 트리거
INSERT INTO outbox (event_type, entity_type, payload)
SELECT 'REBUILD_ALL', 'BRAND', '{}'
UNION ALL
SELECT 'REBUILD_ALL', 'PRODUCT', '{}';
```

---

## 9. RFC-006 준수 검증

| RFC-006 요구사항 | 구현 |
|-----------------|------|
| RefIndexSlice는 lookup 전용 | Brand SUMMARY → Product ENRICHED LOOKUP |
| 1:1 또는 1:small-N | Product 1 : Brand 1 (1:1) |
| JoinSpec 계약 필수 | `join-spec.v1.yaml` 준수 |
| maxRows 명시 | `maxFanout: 10000` |
| required/missingPolicy 명시 | `required: false`, `missingPolicy: PARTIAL_ALLOWED` |
| View 최종 조합 1회 | CORE + ENRICHED 조합 |

---

## 10. 성공 기준

| 지표 | 목표 | 측정 방법 |
|------|------|----------|
| Brand 슬라이싱 | 100% 성공 | Contract 로드 성공 |
| Fanout 동작 | Brand → Product ENRICHED | 통합 테스트 |
| 재빌드 범위 | ENRICHED만 | 버전 비교 |
| View 조합 | Brand 정보 포함 | API 응답 확인 |

---

## 11. 체크리스트

### Day 1

- [ ] `ruleset.brand.v1.yaml` 생성
- [ ] `SliceType.SUMMARY`, `SliceKind.REF_INDEX` 추가
- [ ] `ruleset-product-doc001.v1.yaml`에 ENRICHED 추가
- [ ] `view-product-detail.v1.yaml` 수정
- [ ] Contract 검증 통과

### Day 2

- [ ] `SlicingEngine` SUMMARY/ENRICHED 빌드 로직
- [ ] `JoinExecutor` REF_INDEX 참조 로직
- [ ] Fanout `triggerSlices` 필터링
- [ ] 단위 테스트 작성 및 통과
- [ ] 통합 테스트 작성 및 통과
- [ ] E2E: Brand 변경 → Product View 반영 확인

---

## 12. 참고 문서

- [RFC-006: Slicing Join 허용 범위](./rfc006.md)
- [RFC-001: 슬라이싱 원칙](./rfc001.md)
- [ADR-0006: Slicing Join Scope](../adr/0006-slicing-join-scope.md)
- [DOC-001: Slicing View Policy](./DOC-001-slicing-view-policy.md)
