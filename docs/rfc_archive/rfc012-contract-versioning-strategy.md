# RFC-012: Contract 버전 관리 전략

**Status**: Draft → **ADR로 마이그레이션됨** ([ADR-0011](../adr/0011-contract-versioning-strategy.md))  
**Created**: 2026-01-26  
**Scope**: Contract 버전 관리 전략 및 마이그레이션 정책  
**Audience**: Platform Developers / Contract Authors

> **참고**: 이 RFC는 ADR-0011로 마이그레이션되었습니다. 핵심 결정사항은 ADR을 참고하세요.
> 상세한 설계 내용은 이 RFC 문서를 참고하세요.

---

## 0. Executive Summary

IVM Lite는 **계약 기반(Contract-First)** 시스템으로, 모든 스키마/규칙/플랜이 Contract로 정의됩니다.

본 RFC는 다음 Contract들의 버전 관리 전략을 정의합니다:
- **EntitySchema** (엔티티 스키마)
- **RuleSet** (슬라이싱 규칙)
- **ViewDefinition** (뷰 정의)
- **SinkPlan** (싱크 플랜)
- **SinkRule** (싱크 규칙)
- **IndexSpec** (인덱스 스펙)

---

## 1. 버전 관리 원칙

### 1-1. SemVer 사용

모든 Contract는 **Semantic Versioning (major.minor.patch)** 사용:

```kotlin
data class SemVer(val major: Int, val minor: Int, val patch: Int)
// 예: 1.0.0, 1.1.0, 2.0.0
```

### 1-2. 버전 증가 규칙

| 변경 유형 | 버전 증가 | 예시 |
|-----------|-----------|------|
| **Breaking Change** | MAJOR | 필드 삭제, 타입 변경, Required → Optional |
| **Backward Compatible 추가** | MINOR | 필드 추가 (Optional), Enum 값 추가 |
| **버그 수정/문서** | PATCH | 설명 수정, 오타 수정 |

### 1-3. Contract Status 라이프사이클

```
DRAFT → ACTIVE → DEPRECATED → ARCHIVED
```

| Status | 설명 | 사용 가능 여부 |
|--------|------|----------------|
| `DRAFT` | 작성 중 | ❌ (개발/테스트만) |
| `ACTIVE` | 활성화됨 | ✅ (프로덕션 사용) |
| `DEPRECATED` | 사용 중단 예정 | ⚠️ (기존 사용만, 신규 사용 금지) |
| `ARCHIVED` | 아카이브됨 | ❌ (조회만) |

---

## 2. Contract별 버전 관리 전략

### 2-1. EntitySchema (엔티티 스키마)

**버전 변경 시나리오:**

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| 필드 추가 (Optional) | MINOR | 기존 데이터 호환 |
| 필드 추가 (Required) | MAJOR | 기존 데이터 재생성 필요 |
| 필드 삭제 | MAJOR | 기존 데이터 마이그레이션 필요 |
| 필드 타입 변경 | MAJOR | 기존 데이터 변환 필요 |
| 필드명 변경 | MAJOR | 별칭(alias) 지원 또는 마이그레이션 |

**예시:**
```yaml
# v1.0.0
kind: ENTITY_SCHEMA
id: entity.product.v1
version: 1.0.0
fields:
  - name: sku
    type: string
  - name: price
    type: long

# v1.1.0 (MINOR - Optional 필드 추가)
version: 1.1.0
fields:
  - name: sku
    type: string
  - name: price
    type: long
  - name: salePrice  # 새 필드 (Optional)
    type: long
    required: false

# v2.0.0 (MAJOR - Required 필드 추가)
version: 2.0.0
fields:
  - name: sku
    type: string
  - name: price
    type: long
  - name: currency  # 새 필드 (Required)
    type: string
    required: true
```

### 2-2. RuleSet (슬라이싱 규칙)

**버전 변경 시나리오:**

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Slice 추가 | MINOR | 기존 Slice 유지, 새 Slice 생성 |
| Slice 삭제 | MAJOR | 해당 Slice 의존 뷰 영향 |
| Join 추가 | MINOR | 새 Join만 추가 |
| Join 삭제 | MAJOR | 해당 Join 의존 뷰 영향 |
| Index 추가 | MINOR | 새 인덱스만 추가 |
| Index 삭제 | MAJOR | Fanout 영향 |
| Slice 빌드 로직 변경 | MAJOR | Slice 재생성 필요 |

**예시:**
```yaml
# v1.0.0
kind: RULE_SET
id: ruleset.product.v1
version: 1.0.0
slices:
  - type: CORE
    build: { map: {...} }

# v1.1.0 (MINOR - Slice 추가)
version: 1.1.0
slices:
  - type: CORE
    build: { map: {...} }
  - type: PRICING  # 새 Slice
    build: { map: {...} }

# v2.0.0 (MAJOR - Slice 빌드 로직 변경)
version: 2.0.0
slices:
  - type: CORE
    build: { map: {...} }  # 로직 변경
```

### 2-3. ViewDefinition (뷰 정의)

**버전 변경 시나리오:**

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Optional Slice 추가 | MINOR | 기존 뷰 호환 |
| Required Slice 추가 | MAJOR | 기존 뷰 재생성 필요 |
| Slice 제거 | MAJOR | 해당 뷰 사용 불가 |
| Projection 변경 | MAJOR | 뷰 결과 형식 변경 |

**예시:**
```yaml
# v1.0.0
kind: VIEW_DEFINITION
id: view.product.pdp.v1
version: 1.0.0
requiredSlices:
  - CORE
  - PRICING

# v1.1.0 (MINOR - Optional Slice 추가)
version: 1.1.0
requiredSlices:
  - CORE
  - PRICING
optionalSlices:
  - INVENTORY  # 새 Optional Slice

# v2.0.0 (MAJOR - Required Slice 추가)
version: 2.0.0
requiredSlices:
  - CORE
  - PRICING
  - INVENTORY  # Required로 변경
```

### 2-4. IndexSpec (인덱스 스펙)

**버전 변경 시나리오:**

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Index 추가 | MINOR | 새 인덱스만 추가 |
| Index 삭제 | MAJOR | Fanout 영향 |
| `references` 변경 | MAJOR | 역방향 인덱스 재구성 필요 |
| `selector` 변경 | MAJOR | 인덱스 값 재계산 필요 |

**예시:**
```yaml
# v1.0.0
kind: RULE_SET
indexes:
  - type: product_by_brand
    selector: $.brandCode
    references: brand

# v1.1.0 (MINOR - Index 추가)
indexes:
  - type: product_by_brand
    selector: $.brandCode
    references: brand
  - type: product_by_category  # 새 Index
    selector: $.categoryCode
    references: category

# v2.0.0 (MAJOR - selector 변경)
indexes:
  - type: product_by_brand
    selector: $.brand.brandCode  # 경로 변경
    references: brand
```

### 2-5. SinkPlan / SinkRule

**버전 변경 시나리오:**

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Sink 추가 | MINOR | 새 Sink만 추가 |
| Sink 삭제 | MAJOR | 해당 Sink 중단 |
| Mapping 변경 | MAJOR | Sink 문서 형식 변경 |
| DocId 패턴 변경 | MAJOR | 멱등성 영향 |

---

## 3. 마이그레이션 전략

### 3-1. 단계적 마이그레이션 (권장)

```
1. 새 버전 Contract 배포 (ACTIVE)
   ↓
2. 기존 버전 DEPRECATED 표시
   ↓
3. 데이터 재생성 (새 버전 사용)
   ↓
4. 기존 버전 ARCHIVED (모든 데이터 마이그레이션 완료 후)
```

### 3-2. 동시 버전 지원

**기간**: DEPRECATED → ARCHIVED 전환 전

- 기존 버전: 읽기 전용 (기존 데이터 조회)
- 새 버전: 읽기/쓰기 (신규 데이터 생성)

**예시:**
```kotlin
// 기존 데이터 (v1.0.0)
val oldProduct = queryView(
    viewRef = ViewRef("view.product.pdp.v1", SemVer(1, 0, 0)),
    entityKey = "PRODUCT#tenant#P001"
)

// 신규 데이터 (v1.1.0)
val newProduct = queryView(
    viewRef = ViewRef("view.product.pdp.v1", SemVer(1, 1, 0)),
    entityKey = "PRODUCT#tenant#P002"
)
```

### 3-3. 자동 마이그레이션 (미래)

**목표**: Contract 변경 시 자동으로 데이터 재생성

```
Contract v1.1.0 배포
  ↓
자동 감지: v1.0.0 → v1.1.0 변경
  ↓
영향받는 엔티티 목록 조회
  ↓
배치로 재슬라이싱 (새 버전)
  ↓
검증 완료 후 v1.0.0 DEPRECATED
```

---

## 4. 호환성 검증

### 4-1. Compatibility Matrix

모든 Contract는 `compatMatrix` 필수:

```yaml
compatMatrix:
  - fromVersion: "1.0.0"
    toVersion: "1.1.0"
    compatibility: COMPATIBLE
    breakingChanges: []
  
  - fromVersion: "1.1.0"
    toVersion: "2.0.0"
    compatibility: INCOMPATIBLE
    breakingChanges:
      - type: FIELD_REMOVED
        description: "salePrice 필드 제거됨"
        path: "/fields/salePrice"
```

### 4-2. 자동 호환성 검사 (PR 단계)

```bash
# PR에서 자동 실행
./gradlew validateContractCompatibility

# 결과:
# ✅ COMPATIBLE: entity.product.v1@1.0.0 → 1.1.0
# ❌ INCOMPATIBLE: entity.product.v1@1.1.0 → 2.0.0
#   - Breaking: FIELD_REMOVED (salePrice)
```

### 4-3. Golden Examples

각 Contract는 최소 2개 이상의 Golden Example 필수:

```yaml
goldenExamples:
  - input: { "sku": "SKU-001", "price": 10000 }
    expectedOutput: { "sku": "SKU-001", "price": 10000, "currency": "KRW" }
    expectedHash: "sha256:abc123..."
    description: "기본 케이스"
  
  - input: { "sku": "SKU-002", "price": 0 }
    expectedOutput: { "sku": "SKU-002", "price": 0, "currency": "KRW" }
    expectedHash: "sha256:def456..."
    description: "엣지 케이스 (가격 0)"
```

---

## 5. 버전 관리 워크플로우

### 5-1. Contract 작성

```bash
# 1. Contract YAML 작성
vim src/main/resources/contracts/v1/entity.product.v1.yaml

# 2. 로컬 검증
./gradlew validateContracts

# 3. 호환성 검사 (기존 버전과)
./gradlew checkCompatibility
```

### 5-2. Contract 배포

```bash
# 1. DynamoDB에 배포
./gradlew deployContract \
  --id=entity.product.v1 \
  --version=1.1.0 \
  --status=ACTIVE

# 2. 배포 확인
./gradlew listContracts --id=entity.product.v1
# 출력:
# - entity.product.v1@1.0.0 (DEPRECATED)
# - entity.product.v1@1.1.0 (ACTIVE)
```

### 5-3. 데이터 마이그레이션

```bash
# 1. 영향 범위 분석
./gradlew analyzeMigrationImpact \
  --contract=entity.product.v1 \
  --fromVersion=1.0.0 \
  --toVersion=1.1.0

# 출력:
# 영향받는 엔티티: 1,234개
# 예상 소요 시간: 5분

# 2. 마이그레이션 실행
./gradlew migrateData \
  --contract=entity.product.v1 \
  --fromVersion=1.0.0 \
  --toVersion=1.1.0 \
  --batchSize=100

# 3. 검증
./gradlew verifyMigration \
  --contract=entity.product.v1 \
  --version=1.1.0
```

---

## 6. 버전 관리 도구

### 6-1. Contract CLI

```bash
# Contract 목록 조회
ivm-lite contracts list

# 특정 Contract 버전 히스토리
ivm-lite contracts history entity.product.v1

# Contract Diff
ivm-lite contracts diff \
  entity.product.v1@1.0.0 \
  entity.product.v1@1.1.0

# 호환성 검사
ivm-lite contracts check-compatibility \
  entity.product.v1@1.0.0 \
  entity.product.v1@1.1.0
```

### 6-2. Admin API

```kotlin
// Contract 버전 조회
GET /api/admin/contracts/{id}/versions

// Contract 배포
POST /api/admin/contracts
{
  "id": "entity.product.v1",
  "version": "1.1.0",
  "status": "ACTIVE",
  "spec": {...}
}

// Contract 상태 변경
PATCH /api/admin/contracts/{id}/versions/{version}/status
{
  "status": "DEPRECATED"
}
```

---

## 7. Best Practices

### 7-1. 버전 네이밍

- **EntitySchema**: `entity.{domain}.v{version}`
  - 예: `entity.product.v1`, `entity.brand.v1`
- **RuleSet**: `ruleset.{entityType}.v{version}`
  - 예: `ruleset.product.v1`, `ruleset.product.v2`
- **ViewDefinition**: `view.{domain}.{viewName}.v{version}`
  - 예: `view.product.pdp.v1`, `view.product.search.v1`

### 7-2. 버전 증가 시점

- **MAJOR**: Breaking change 감지 시 즉시 증가
- **MINOR**: 기능 추가 시 (다음 릴리스)
- **PATCH**: 버그 수정 시 (즉시)

### 7-3. DEPRECATED 전환 기준

- 최소 **2주** 이상 ACTIVE 유지 후 DEPRECATED
- 모든 데이터 마이그레이션 완료 후 ARCHIVED

### 7-4. 롤백 전략

```bash
# 1. 새 버전 DEPRECATED
ivm-lite contracts deprecate entity.product.v1@1.1.0

# 2. 이전 버전 ACTIVE 복구
ivm-lite contracts activate entity.product.v1@1.0.0

# 3. 데이터 롤백 (필요 시)
ivm-lite migrate --rollback \
  --contract=entity.product.v1 \
  --fromVersion=1.1.0 \
  --toVersion=1.0.0
```

---

## 8. 예외 상황

### 8-1. 긴급 수정 (Hotfix)

**시나리오**: 프로덕션 버그 긴급 수정

```bash
# PATCH 버전 증가 (1.0.0 → 1.0.1)
ivm-lite contracts deploy \
  --id=entity.product.v1 \
  --version=1.0.1 \
  --status=ACTIVE \
  --hotfix=true

# 기존 1.0.0은 즉시 DEPRECATED
```

### 8-2. 다중 버전 병행

**시나리오**: A/B 테스트, Canary 배포

```yaml
# v1.0.0 (90% 트래픽)
status: ACTIVE
trafficWeight: 90

# v1.1.0 (10% 트래픽)
status: ACTIVE
trafficWeight: 10
```

---

## 9. 체크리스트

### Contract 작성 시

- [ ] SemVer 형식 준수 (`major.minor.patch`)
- [ ] `compatMatrix` 정의
- [ ] `goldenExamples` 최소 2개 이상
- [ ] Breaking change는 MAJOR 버전 증가
- [ ] 로컬 검증 통과 (`validateContracts`)

### Contract 배포 시

- [ ] 호환성 검사 통과 (`checkCompatibility`)
- [ ] DynamoDB 배포 완료
- [ ] Status = ACTIVE 확인
- [ ] 캐시 무효화 (필요 시)

### 마이그레이션 시

- [ ] 영향 범위 분석 완료
- [ ] 데이터 마이그레이션 완료
- [ ] 검증 완료
- [ ] 기존 버전 DEPRECATED 전환
- [ ] 모니터링 설정

---

## 10. 참고

- [RFC-003: Contract Schema 확장](./rfc003.md) - Contract 스키마 정의
- [RFC-IMPL-002: Contract Registry v1](./rfcimpl002.md) - Registry 구현
- [RFC-IMPL-007: DynamoDB Contract Registry](./rfcimpl007.md) - DynamoDB 어댑터
- [Semantic Versioning 2.0.0](https://semver.org/)

---

**문의**: Contract 버전 관리 관련 문의는 #ivm-platform 채널로 연락주세요.
