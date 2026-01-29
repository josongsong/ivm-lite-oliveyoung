# DynamoDB에 올려야 할 계약 세트 (Contract Set)

## 개요

DynamoDB E2E 테스트 및 운영 환경에서 필요한 모든 계약(Contract) 목록입니다.

## 필수 계약 목록

### 1. RuleSet Contract
- **ID**: `ruleset.core.v1`
- **버전**: `1.0.0`
- **파일**: `src/main/resources/contracts/v1/ruleset.v1.yaml`
- **용도**: Product 엔티티의 Slice 생성 규칙 및 Inverted Index 정의
- **핵심 내용**:
  - Slice 타입: CORE, PRICE, INVENTORY, MEDIA, CATEGORY
  - Inverted Index: `brand`, `category` (references 포함)
  - Fanout 설정: `maxFanout: 10000` (brand), `maxFanout: 50000` (category)

### 2. ChangeSet Contract
- **ID**: `changeset.v1`
- **버전**: `1.0.0`
- **파일**: `src/main/resources/contracts/v1/changeset.v1.yaml`
- **용도**: ChangeSet 스키마 정의 및 Fanout 설정
- **핵심 내용**:
  - Entity Key 형식: `{ENTITY_TYPE}#{tenantId}#{entityId}`
  - ChangeSet 필드 정의
  - Fanout 활성화 설정

### 3. JoinSpec Contract
- **ID**: `join-spec.v1`
- **버전**: `1.0.0`
- **파일**: `src/main/resources/contracts/v1/join-spec.v1.yaml`
- **용도**: JOIN 연산 규칙 정의
- **핵심 내용**:
  - JOIN 타입: LOOKUP만 허용
  - 최대 JOIN 깊이: 1
  - Fanout 설정 (deprecated, RuleSet.indexes로 이동)

## 선택적 계약 (ViewDefinition 등)

다음 계약들은 QueryViewWorkflow에서 사용되지만, 기본 E2E 테스트에는 필수는 아닙니다:

- `view-definition.v1.yaml` - ViewDefinition 기본 스키마
- `view-product-search.v1.yaml` - Product 검색 뷰
- `view-product-cart.v1.yaml` - 장바구니 뷰
- `view-brand-detail.v1.yaml` - Brand 상세 뷰
- `entity-product.v1.yaml` - Product 엔티티 스키마
- `entity-brand.v1.yaml` - Brand 엔티티 스키마
- `entity-category.v1.yaml` - Category 엔티티 스키마
- `index-value-canonicalizer.v1.yaml` - 인덱스 값 정규화 규칙

## DynamoDB 테이블 구조

### 테이블명
- **Remote-only**: `${DYNAMODB_TABLE}` (예: `ivm-lite-schema-registry-dev`, `ivm-lite-schema-registry-prod`)

### 키 스키마
- **PK**: `{kind}#{id}` (예: `RULESET#ruleset.core.v1`)
- **SK**: `version` (예: `1.0.0`)

### 필수 속성
- `kind`: 계약 종류 (RULESET, CHANGESET, JOIN_SPEC, VIEW_DEFINITION 등)
- `id`: 계약 ID
- `version`: SemVer 버전
- `status`: ACTIVE / DEPRECATED / DRAFT
- `spec`: 계약 본문 (Map/JSON 형태)
- `checksum`: spec의 SHA256 해시 (무결성 검증)

## 업로드 방법

### 방법 1: Seed 스크립트 사용 (권장)

```bash
# Remote-only: DYNAMODB_TABLE 환경 변수를 설정한 뒤 실행
./scripts/seed-contracts.sh
```

### 방법 2: 수동 업로드 (AWS CLI)

```bash
# 예시: ruleset.core.v1 업로드
aws dynamodb put-item \
  --table-name "$DYNAMODB_TABLE" \
  --item '{
    "PK": {"S": "RULESET#ruleset.core.v1"},
    "SK": {"S": "1.0.0"},
    "kind": {"S": "RULESET"},
    "id": {"S": "ruleset.core.v1"},
    "version": {"S": "1.0.0"},
    "status": {"S": "ACTIVE"},
    "spec": {...},  # YAML을 JSON으로 변환한 내용
    "checksum": {"S": "..."}  # spec의 SHA256 해시
  }'
```

### 방법 3: DynamoDBContractRegistryAdapter.putContract 사용

코드에서 직접 업로드:

```kotlin
val adapter = DynamoDBContractRegistryAdapter(dynamoClient, tableName)
val contract = // YAML 파일에서 로드
adapter.putContract(contract)
```

## 최소 필수 계약 세트 (E2E 테스트용)

DynamoDbE2ETest(IntegrationTag)를 실행하기 위해 최소한 다음 3개 계약이 필요합니다:

1. ✅ **RULESET#ruleset.core.v1** (version: 1.0.0)
2. ✅ **CHANGESET#changeset.v1** (version: 1.0.0)
3. ✅ **JOIN_SPEC#join-spec.v1** (version: 1.0.0)

## 검증

업로드 후 다음 명령으로 검증:

```bash
# DynamoDB에서 계약 조회 테스트
./gradlew test --tests "com.oliveyoung.ivmlite.pkg.contracts.DynamoDBContractRegistryAdapterTest"

# E2E 테스트 실행
E2E_RUN=true DYNAMODB_ENDPOINT=... ./gradlew integrationTest --tests "com.oliveyoung.ivmlite.integration.DynamoDbE2ETest"
```

## 참고

- RFC-IMPL-007: DynamoDB Schema Registry 설계 문서
- `src/main/kotlin/com/oliveyoung/ivmlite/pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt`: 구현 코드
- `src/main/resources/contracts/v1/*.yaml`: 소스 YAML 파일들
