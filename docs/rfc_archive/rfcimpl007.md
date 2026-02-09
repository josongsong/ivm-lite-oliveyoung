RFC-IMPL-007 — DynamoDB Schema Registry (Contract Registry v2)

Status: Accepted
Created: 2026-01-25
Scope: 운영 환경용 DynamoDB 기반 Schema Registry 구현
Depends on: RFC-V4-002, RFC-V4-003, RFC-IMPL-001, RFC-IMPL-002
Audience: Platform / Infra / Runtime Developers
Non-Goals: Registry UI, 계약 작성 도구, 멀티 리전 동기화 (v3 이후)

---

0. Executive Summary

본 RFC는 **운영 환경**에서 스키마/계약을 관리하기 위한 **DynamoDB 기반 Schema Registry**를 정의한다.

v1의 `LocalYamlContractRegistryAdapter`는 개발/테스트용이며,
**운영 SSOT는 DynamoDB**에서 조회한다.

핵심 원칙:
- **포트 불변**: `ContractRegistryPort` 인터페이스는 변경 없음 (어댑터만 교체)
- **버전 관리**: 스키마는 immutable, 버전별로 별도 항목
- **Fail-closed**: 없는 스키마 조회 시 즉시 오류 (silent fallback 금지)
- **캐싱**: 런타임 성능을 위해 로컬 캐싱 (TTL 기반)

---

1. DynamoDB Table Design

1-1. Table Name
- `ivm-lite-schema-registry-{env}` (예: `-dev`, `-prod`)

1-2. Key Schema
- **Partition Key (PK)**: `contractKind#contractId` (예: `CHANGESET#changeset.v1`)
- **Sort Key (SK)**: `version` (SemVer 문자열, 예: `1.0.0`)

1-3. Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| PK | S | `{kind}#{id}` |
| SK | S | SemVer version |
| kind | S | Contract kind (CHANGESET, JOIN_SPEC, INVERTED_INDEX, ...) |
| id | S | Contract ID |
| version | S | SemVer version |
| status | S | ACTIVE / DEPRECATED / DRAFT |
| spec | M | 계약 본문 (Map 형태) |
| createdAt | S | ISO8601 timestamp |
| createdBy | S | 작성자 ID (감사 추적) |
| checksum | S | spec의 SHA256 해시 (무결성 검증) |

1-4. GSI (Global Secondary Index)
- **GSI1**: `kind-status-index`
  - PK: `kind`
  - SK: `status`
  - 용도: "모든 ACTIVE CHANGESET 조회" 등

---

2. Access Patterns

2-1. 단일 계약 조회 (가장 빈번)
- Query: `PK = {kind}#{id}, SK = {version}`
- 예: `PK = CHANGESET#changeset.v1, SK = 1.0.0`

2-2. 특정 ID의 모든 버전 조회
- Query: `PK = {kind}#{id}` (SK 조건 없음)
- 예: 버전 목록 확인, 최신 버전 조회

2-3. 특정 Kind의 ACTIVE 목록
- GSI1 Query: `kind = CHANGESET, status = ACTIVE`
- 용도: 관리 UI, 목록 조회

---

3. DynamoDBContractRegistryAdapter

3-1. 구현 요구사항

```kotlin
class DynamoDBContractRegistryAdapter(
    private val dynamoDbClient: DynamoDbClient,
    private val tableName: String,
    private val cache: ContractCache, // 선택적
) : ContractRegistryPort {

    override suspend fun loadChangeSetContract(ref: ContractRef): Result<ChangeSetContract> {
        // 1. 캐시 확인 (hit 시 반환)
        // 2. DynamoDB GetItem
        // 3. Fail-closed: 없으면 Err 반환
        // 4. spec → ChangeSetContract 역직렬화
        // 5. checksum 검증 (무결성)
        // 6. 캐시 저장
        // 7. Ok 반환
    }
    
    // loadJoinSpecContract, loadInvertedIndexContract 동일 패턴
}
```

3-2. Fail-Closed 규칙
- 계약 없음 → `Err(ContractNotFound(ref))`
- 역직렬화 실패 → `Err(ContractCorrupted(ref, reason))`
- checksum 불일치 → `Err(ContractIntegrityError(ref))`
- **절대로 silent fallback 하지 않음**

3-3. 캐싱 전략
- **로컬 인메모리 캐시** (LRU + TTL)
- TTL: 운영 5분, 개발 30초 (설정 가능)
- 캐시 키: `{kind}:{id}:{version}`
- **캐시 무효화**: 명시적 API 제공 (배포 시 호출)

---

4. Migration: v1 (YAML) → v2 (DynamoDB)

4-1. Seed 스크립트
- `tooling/`에 `SeedContractsToDynamoDB` 유틸 구현
- 로컬 YAML 파일을 읽어서 DynamoDB에 PutItem
- 이미 존재하면 skip (멱등)

4-2. 마이그레이션 순서
1. DynamoDB 테이블 생성 (IaC)
2. Seed 스크립트로 v1 YAML → DynamoDB import
3. `DynamoDBContractRegistryAdapter` 구현 및 테스트
4. DI 설정에서 어댑터 교체
5. 로컬 YAML은 "개발 seed" 용도로만 유지

4-3. Rollback
- DI 설정만 원복하면 `LocalYamlContractRegistryAdapter`로 즉시 전환
- 포트가 동일하므로 코드 변경 없음

---

5. Infrastructure (IaC)

5-1. Terraform/CDK 예시 (참고용)

```hcl
resource "aws_dynamodb_table" "schema_registry" {
  name         = "ivm-lite-schema-registry-${var.env}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "kind"
    type = "S"
  }
  attribute {
    name = "status"
    type = "S"
  }

  global_secondary_index {
    name            = "kind-status-index"
    hash_key        = "kind"
    range_key       = "status"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }
  
  tags = {
    Service = "ivm-lite"
    Component = "schema-registry"
  }
}
```

---

6. Acceptance Criteria

6-1. 어댑터 구현
- [ ] `DynamoDBContractRegistryAdapter` 구현 완료
- [ ] `ContractRegistryPort` 인터페이스 100% 구현
- [ ] 단위 테스트 (LocalStack 또는 DynamoDB Local)

6-2. Fail-Closed
- [ ] 없는 계약 조회 → `Err(ContractNotFound)`
- [ ] 손상된 계약 → `Err(ContractCorrupted)`
- [ ] checksum 불일치 → `Err(ContractIntegrityError)`

6-3. 캐싱
- [ ] 캐시 hit 시 DynamoDB 호출 없음
- [ ] TTL 만료 후 재조회
- [ ] 명시적 캐시 무효화 API

6-4. Migration
- [ ] Seed 스크립트로 YAML → DynamoDB 성공
- [ ] 멱등 (재실행해도 오류 없음)
- [ ] Rollback 테스트 (어댑터 교체 후 동일 동작)

---

7. Open Questions (향후 RFC)

- **멀티 리전 동기화**: Global Tables vs 명시적 복제?
- **버전 승격 워크플로우**: DRAFT → ACTIVE 승격 시 검증 절차?
- **계약 삭제 정책**: DEPRECATED 후 얼마 후 삭제? 또는 영구 보관?
- **Registry UI**: 별도 서비스 또는 Admin API에 통합?

---

8. Summary

| 항목 | v1 (LocalYaml) | v2 (DynamoDB) |
|------|----------------|---------------|
| SSOT | `resources/contracts/v1/*.yaml` | DynamoDB |
| 용도 | 개발/테스트/부트스트랩 | 운영 |
| 어댑터 | `LocalYamlContractRegistryAdapter` | `DynamoDBContractRegistryAdapter` |
| 포트 | `ContractRegistryPort` (동일) | `ContractRegistryPort` (동일) |
| 전환 | - | DI 설정 변경만 |

**포트 불변 → 어댑터만 교체 → 무중단 전환**
