# RFC-IMPL-013: InvertedIndexContract → IndexSpec.references 통합

**Status**: Implemented
**Created**: 2026-01-27
**Scope**: InvertedIndexContract를 IndexSpec.references로 통합하여 단순화

---

## 0. Executive Summary

본 RFC는 별도로 관리되던 `InvertedIndexContract`를 `RuleSet.indexes`의 `IndexSpec.references` 필드로 통합합니다.

### 변경 전 (복잡)
```yaml
# 1. RuleSet.indexes (정방향 인덱스만)
indexes:
  - type: brand
    selector: $.brandId

# 2. 별도 InvertedIndexContract (역방향 인덱스)
kind: InvertedIndexContract
pkPattern: "REF#{refEntityType}#..."
```

### 변경 후 (단순)
```yaml
# RuleSet.indexes (정방향 + 역방향 통합)
indexes:
  - type: brand
    selector: $.brandId
    references: BRAND      # FK 엔티티 → 역방향 인덱스 자동 생성
    maxFanout: 10000       # circuit breaker 임계값
```

---

## 1. Motivation

### 1.1 문제점

1. **중복 관리**: 동일한 FK 관계를 2곳에서 관리 (IndexSpec, InvertedIndexContract)
2. **학습 곡선**: 별도 계약 개념 이해 필요
3. **실수 가능성**: 수동 동기화 필요, 불일치 위험
4. **오버 엔지니어링**: 실질적 이점 없이 복잡성 증가

### 1.2 해결책

`IndexSpec`에 `references` 필드를 추가하여 FK 관계를 명시하면, 정방향/역방향 인덱스가 자동 생성됩니다.

---

## 2. Changes

### 2.1 IndexSpec 확장

```kotlin
data class IndexSpec(
    val type: String,
    val selector: String,
    val references: String? = null,  // 🆕 FK 엔티티 타입
    val maxFanout: Int = 10000,       // 🆕 circuit breaker 임계값
)
```

### 2.2 InvertedIndexBuilder 개선

```kotlin
fun build(slice, indexSpecs, entityType): List<InvertedIndexEntry> {
    return indexSpecs.flatMap { spec ->
        val values = extractValues(slice.data, spec.selector)
        values.flatMap { value ->
            buildList {
                // 1. 정방향 인덱스 (항상 생성)
                add(forwardIndex)

                // 2. 역방향 인덱스 (references가 있을 때만)
                if (spec.references != null) {
                    add(reverseIndex)
                }
            }
        }
    }
}
```

### 2.3 인덱스 생성 규칙

| IndexSpec | 정방향 인덱스 | 역방향 인덱스 |
|-----------|-------------|-------------|
| `type: brand, selector: $.brandId` | ✅ 생성 | ❌ 없음 |
| `type: brand, selector: $.brandId, references: BRAND` | ✅ 생성 | ✅ 자동 생성 |

### 2.4 역방향 인덱스 키 형식

```
indexType: "{entityType}_by_{references}"
예: product_by_brand

refEntityKey: "{REFERENCES}#{tenantId}#{fkValue}"
예: BRAND#tenant1#br001

targetEntityKey: 현재 엔티티 키
예: PRODUCT#tenant1#prod001
```

---

## 3. Deprecation

### 3.1 Deprecated Items

| 항목 | 상태 | 대안 |
|------|------|------|
| `InvertedIndexContract` | @Deprecated | `IndexSpec.references` |
| `loadInvertedIndexContract()` | @Deprecated | N/A (자동 생성) |
| `inverted-index.v1.yaml` | 삭제됨 | RuleSet.indexes |
| `JoinSpecContract.invertedIndexRef` | @Deprecated(nullable) | `IndexSpec.references` |

### 3.2 Migration

1. RuleSet YAML의 indexes에 `references` 추가
2. `InvertedIndexContract` 관련 코드 제거 (하위 호환성 유지)
3. `inverted-index.v1.yaml` 삭제

---

## 4. Benefits

| 항목 | Before | After |
|------|--------|-------|
| 설정 위치 | 2곳 | 1곳 |
| FK 관계 명시 | 암묵적 | 명시적 |
| 역방향 인덱스 생성 | 수동 | 자동 |
| 학습 곡선 | 높음 | 낮음 |
| 실수 가능성 | 높음 | 낮음 |

---

## 5. Example

### 5.1 RuleSet YAML

```yaml
kind: RULESET
id: ruleset.core.v1
version: 1.0.0
status: ACTIVE

entityType: PRODUCT

indexes:
  - type: brand
    selector: $.brand
    references: BRAND        # Brand 변경 시 → Product 자동 재슬라이싱
    maxFanout: 10000
  - type: category
    selector: $.categoryId
    references: CATEGORY     # Category 변경 시 → Product 자동 재슬라이싱
    maxFanout: 50000
  - type: tag
    selector: $.tags[*]
    # references 없음 → 검색용 인덱스만 (Fanout 없음)
```

### 5.2 자동 생성되는 인덱스

| 설정 | 정방향 인덱스 | 역방향 인덱스 |
|------|-------------|-------------|
| `brand, references: BRAND` | `brand: br001 → PRODUCT#...` | `product_by_brand: br001 → PRODUCT#...` |
| `category, references: CATEGORY` | `category: cat01 → PRODUCT#...` | `product_by_category: cat01 → PRODUCT#...` |
| `tag` (no references) | `tag: summer → PRODUCT#...` | ❌ 없음 |

---

## 6. Files Changed

### 6.1 Modified

- `RuleSetContract.kt`: IndexSpec에 references, maxFanout 추가
- `Contracts.kt`: InvertedIndexContract @Deprecated
- `InvertedIndexBuilder.kt`: 역방향 인덱스 자동 생성 로직
- `InvertedIndexKeys.kt`: InvertedIndexContract 의존 제거
- `ContractRegistryPort.kt`: loadInvertedIndexContract @Deprecated
- `DynamoDBContractRegistryAdapter.kt`: indexes 파싱 확장
- `LocalYamlContractRegistryAdapter.kt`: indexes 파싱 확장
- `GatedContractRegistryAdapter.kt`: @Deprecated 전파
- `ruleset.v1.yaml`: indexes에 references 추가
- `join-spec.v1.yaml`: invertedIndexRef deprecated 표시

### 6.2 Deleted

- `inverted-index.v1.yaml`: 삭제됨 (통합으로 인해 불필요)

---

## 7. Testing

```bash
# 빌드 성공
./gradlew compileKotlin compileTestKotlin  # ✅

# InvertedIndexBuilder 테스트 통과
./gradlew test --tests "InvertedIndexBuilderTest"  # ✅

# 멱등성 테스트 통과
./gradlew test --tests "IdempotencyPropertyTest"  # ✅
```

---

## 8. Conclusion

`InvertedIndexContract`를 `IndexSpec.references`로 통합하여:

1. **DRY**: FK 관계를 한 번만 선언
2. **자동화**: 역방향 인덱스 자동 생성
3. **단순화**: 별도 계약 관리 불필요
4. **명확성**: DSL에서 FK 관계가 명시적
