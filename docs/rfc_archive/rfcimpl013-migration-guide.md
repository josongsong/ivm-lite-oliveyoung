# RFC-IMPL-013: InvertedIndexContract → IndexSpec.references 마이그레이션 가이드

## 1. 개요

이 가이드는 `InvertedIndexContract`를 `IndexSpec.references`로 마이그레이션하는 방법을 설명합니다.

### 변경 전
```yaml
# 별도 파일: inverted-index.v1.yaml
kind: InvertedIndexContract
pkPattern: "REF#{refEntityType}#..."

# RuleSet의 indexes (references 없음)
indexes:
  - type: brand
    selector: $.brandId
```

### 변경 후
```yaml
# RuleSet의 indexes (references 추가)
indexes:
  - type: brand
    selector: $.brandId
    references: BRAND      # FK 엔티티 타입
    maxFanout: 10000       # circuit breaker 임계값
```

---

## 2. 마이그레이션 단계

### 2.1 RuleSet YAML 업데이트

모든 RuleSet의 `indexes` 섹션에서 FK 관계가 있는 인덱스에 `references` 필드를 추가합니다.

**Before:**
```yaml
indexes:
  - type: brand
    selector: $.brandId
  - type: category
    selector: $.categoryId
  - type: tag
    selector: $.tags[*]
```

**After:**
```yaml
indexes:
  - type: brand
    selector: $.brandId
    references: BRAND        # Brand 변경 → Product 재슬라이싱
    maxFanout: 10000
  - type: category
    selector: $.categoryId
    references: CATEGORY     # Category 변경 → Product 재슬라이싱
    maxFanout: 50000
  - type: tag
    selector: $.tags[*]
    # references 없음 → 검색용 인덱스만
```

### 2.2 inverted-index.v1.yaml 삭제

더 이상 필요하지 않습니다. 삭제하거나 deprecated로 표시합니다.

```bash
rm src/main/resources/contracts/v1/inverted-index.v1.yaml
```

### 2.3 join-spec.v1.yaml 업데이트

`invertedIndex.contractRef`를 제거합니다.

**Before:**
```yaml
fanout:
  invertedIndex:
    required: true
    contractRef: { id: inverted-index.v1, version: 1.0.0 }
```

**After:**
```yaml
fanout:
  invertedIndex:
    required: false  # deprecated
```

---

## 3. 역방향 인덱스 재생성

기존 데이터가 있는 경우, 역방향 인덱스를 재생성해야 합니다.

### 3.1 재생성 스크립트 (예시)

```kotlin
// 모든 Slice를 다시 처리하여 역방향 인덱스 생성
suspend fun regenerateInvertedIndexes(
    sliceRepo: SliceRepositoryPort,
    invertedIndexRepo: InvertedIndexRepositoryPort,
    ruleSetContract: RuleSetContract,
) {
    val builder = InvertedIndexBuilder()
    
    // 모든 Slice 조회
    val allSlices = sliceRepo.listAll()
    
    for (slice in allSlices) {
        // references가 있는 IndexSpec만 필터
        val indexSpecs = ruleSetContract.indexes.filter { it.references != null }
        
        // 역방향 인덱스 생성
        val indexes = builder.build(slice, indexSpecs)
        
        // 저장
        invertedIndexRepo.putAllIdempotent(indexes)
    }
}
```

### 3.2 배치 재처리

대량의 데이터가 있는 경우, 배치로 처리합니다:

```kotlin
// 배치 크기
val batchSize = 1000

// 페이지네이션으로 처리
var cursor: String? = null
do {
    val page = sliceRepo.listPage(cursor, batchSize)
    
    for (slice in page.items) {
        val indexes = builder.build(slice, indexSpecs)
        invertedIndexRepo.putAllIdempotent(indexes)
    }
    
    cursor = page.nextCursor
} while (cursor != null)
```

---

## 4. 검증

### 4.1 인덱스 생성 확인

```kotlin
// Brand "BR001"을 참조하는 Product 조회
val result = invertedIndexRepo.queryByIndexType(
    tenantId = tenantId,
    indexType = "product_by_brand",
    indexValue = "br001",  // entityId만 (소문자)
)

println("영향받는 Product 수: ${result.entries.size}")
```

### 4.2 Fanout 테스트

```kotlin
// Brand 변경 시 Product가 재슬라이싱되는지 확인
val result = fanoutWorkflow.onEntityChange(
    tenantId = tenantId,
    upstreamEntityType = "BRAND",
    upstreamEntityKey = EntityKey("BRAND#tenant#BR001"),
    upstreamVersion = 2L,
)

println("재슬라이싱된 Product 수: ${result.processedCount}")
```

---

## 5. 롤백 계획

문제 발생 시 롤백:

1. RuleSet에서 `references` 필드 제거
2. `inverted-index.v1.yaml` 복원
3. `@Deprecated` 어노테이션 제거

---

## 6. FAQ

### Q: 기존 인덱스는 자동으로 마이그레이션되나요?

A: 아니요. 기존 인덱스는 `indexValue`가 전체 EntityKey 형식일 수 있습니다. 새 형식은 `entityId`만 사용합니다. 재생성이 필요합니다.

### Q: references가 없는 인덱스는 어떻게 되나요?

A: 검색용 정방향 인덱스만 생성됩니다. Fanout에는 사용되지 않습니다.

### Q: maxFanout의 기본값은?

A: 10000입니다. 카테고리처럼 연관 엔티티가 많은 경우 높게 설정하세요.

---

## 7. 체크리스트

- [ ] RuleSet YAML에 `references` 필드 추가
- [ ] `inverted-index.v1.yaml` 삭제
- [ ] `join-spec.v1.yaml` 업데이트
- [ ] 기존 역방향 인덱스 재생성
- [ ] E2E 테스트 통과 확인
- [ ] Fanout 동작 확인
