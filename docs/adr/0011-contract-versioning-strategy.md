# ADR-0011: Contract 버전 관리 전략

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-012

---

## Context

IVM Lite는 **계약 기반(Contract-First)** 시스템으로, 모든 스키마/규칙/플랜이 Contract로 정의됩니다.

다음 Contract들의 버전 관리 전략이 필요했습니다:
- EntitySchema (엔티티 스키마)
- RuleSet (슬라이싱 규칙)
- ViewDefinition (뷰 정의)
- SinkPlan (싱크 플랜)
- SinkRule (싱크 규칙)
- IndexSpec (인덱스 스펙)

## Decision

**Contract 버전 관리 전략**을 정의합니다.

### 버전 관리 원칙

#### SemVer 사용

모든 Contract는 **Semantic Versioning (major.minor.patch)** 사용:

```kotlin
data class SemVer(val major: Int, val minor: Int, val patch: Int)
// 예: 1.0.0, 1.1.0, 2.0.0
```

#### 버전 증가 규칙

| 변경 유형 | 버전 증가 | 예시 |
|-----------|-----------|------|
| **Breaking Change** | MAJOR | 필드 삭제, 타입 변경, Required → Optional |
| **Backward Compatible 추가** | MINOR | 필드 추가 (Optional), Enum 값 추가 |
| **버그 수정/문서** | PATCH | 설명 수정, 오타 수정 |

#### Contract Status 라이프사이클

```
DRAFT → ACTIVE → DEPRECATED → ARCHIVED
```

| Status | 설명 | 사용 가능 여부 |
|--------|------|----------------|
| `DRAFT` | 작성 중 | ❌ (개발/테스트만) |
| `ACTIVE` | 활성화됨 | ✅ (프로덕션 사용) |
| `DEPRECATED` | 사용 중단 예정 | ⚠️ (기존 사용만, 신규 사용 금지) |
| `ARCHIVED` | 아카이브됨 | ❌ (조회만) |

### Contract별 버전 관리 전략

#### EntitySchema (엔티티 스키마)

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| 필드 추가 (Optional) | MINOR | 기존 데이터 호환 |
| 필드 추가 (Required) | MAJOR | 기존 데이터 재생성 필요 |
| 필드 삭제 | MAJOR | 기존 데이터 마이그레이션 필요 |
| 필드 타입 변경 | MAJOR | 기존 데이터 변환 필요 |
| 필드명 변경 | MAJOR | 별칭(alias) 지원 또는 마이그레이션 |

#### RuleSet (슬라이싱 규칙)

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Slice 추가 | MINOR | 기존 Slice 유지, 새 Slice 생성 |
| Slice 삭제 | MAJOR | 해당 Slice 의존 뷰 영향 |
| Join 추가 | MINOR | 새 Join만 추가 |
| Join 삭제 | MAJOR | 해당 Join 의존 뷰 영향 |
| Index 추가 | MINOR | 새 인덱스만 추가 |
| Index 삭제 | MAJOR | Fanout 영향 |
| Slice 빌드 로직 변경 | MAJOR | Slice 재생성 필요 |

#### ViewDefinition (뷰 정의)

| 변경 | 버전 | 영향 범위 |
|------|------|-----------|
| Optional Slice 추가 | MINOR | 기존 뷰 호환 |
| Required Slice 추가 | MAJOR | 기존 뷰 재생성 필요 |
| Slice 제거 | MAJOR | 해당 뷰 사용 불가 |
| Projection 변경 | MAJOR | 뷰 결과 형식 변경 |

### 마이그레이션 전략

#### 단계적 마이그레이션 (권장)

```
1. 새 버전 Contract 배포 (ACTIVE)
   ↓
2. 기존 버전 DEPRECATED 표시
   ↓
3. 데이터 재생성 (새 버전 사용)
   ↓
4. 기존 버전 ARCHIVED (모든 데이터 마이그레이션 완료 후)
```

#### 동시 버전 지원

**기간**: DEPRECATED → ARCHIVED 전환 전

- 기존 버전: 읽기 전용 (기존 데이터 조회)
- 새 버전: 읽기/쓰기 (신규 데이터 생성)

### 호환성 검증

#### Compatibility Matrix

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

#### 자동 호환성 검사 (PR 단계)

```bash
# PR에서 자동 실행
./gradlew validateContractCompatibility

# 결과:
# ✅ COMPATIBLE: entity.product.v1@1.0.0 → 1.1.0
# ❌ INCOMPATIBLE: entity.product.v1@1.1.0 → 2.0.0
#   - Breaking: FIELD_REMOVED (salePrice)
```

#### Golden Examples

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

### 버전 관리 워크플로우

#### Contract 작성

```bash
# 1. Contract YAML 작성
vim src/main/resources/contracts/v1/entity.product.v1.yaml

# 2. 로컬 검증
./gradlew validateContracts

# 3. 호환성 검사 (기존 버전과)
./gradlew checkCompatibility
```

#### Contract 배포

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

#### 데이터 마이그레이션

```bash
# 1. 영향 범위 분석
./gradlew analyzeMigrationImpact \
  --contract=entity.product.v1 \
  --fromVersion=1.0.0 \
  --toVersion=1.1.0

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

### Best Practices

#### 버전 네이밍

- **EntitySchema**: `entity.{domain}.v{version}`
- **RuleSet**: `ruleset.{entityType}.v{version}`
- **ViewDefinition**: `view.{domain}.{viewName}.v{version}`

#### 버전 증가 시점

- **MAJOR**: Breaking change 감지 시 즉시 증가
- **MINOR**: 기능 추가 시 (다음 릴리스)
- **PATCH**: 버그 수정 시 (즉시)

#### DEPRECATED 전환 기준

- 최소 **2주** 이상 ACTIVE 유지 후 DEPRECATED
- 모든 데이터 마이그레이션 완료 후 ARCHIVED

## Consequences

### Positive

- ✅ SemVer로 버전 관리 일관성 확보
- ✅ 호환성 검증으로 안전한 업그레이드 보장
- ✅ 단계적 마이그레이션으로 무중단 업그레이드 가능
- ✅ Golden Examples로 계약 검증 자동화

### Negative

- ⚠️ 버전 관리 오버헤드
- ⚠️ 마이그레이션 계획 및 실행 필요
- ⚠️ 호환성 검증 복잡도

### Neutral

- 버전 관리 도구 개발 비용
- 마이그레이션 실행 시간

---

## 참고

- [RFC-012](../rfc/rfc012-contract-versioning-strategy.md) - 원본 RFC 문서
- [RFC-V4-003](../rfc/rfc003.md) - Contract Enhancement
- [Semantic Versioning 2.0.0](https://semver.org/)
