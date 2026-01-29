# ivm-lite (oliveyoung.com)

Contract-first deterministic data runtime:
RawData → Snapshot/ChangeSet → Slice → Virtual View → (v4.1) CDC → Sink

## Quickstart

### 0) AWS 자격 증명 설정 (SOTA급 자동 설정) ⭐

```bash
# .env 파일 자동 생성 및 환경 변수 로드
source scripts/load-env.sh

# 또는 환경 변수 자동 로드 후 애플리케이션 실행
./scripts/run-with-env.sh ./gradlew run
```

> 💡 **자세한 설정 방법**: [AWS 자격 증명 설정 가이드](./docs/aws-credentials-setup.md) 참고

### 1) 인프라 시작
```bash
# PostgreSQL + DynamoDB + Kafka + Debezium 시작
docker-compose up -d

# 또는 전체 설정 스크립트 (테이블 생성 + Debezium 등록 포함)
./infra/setup-local.sh
```

### 2) DB 마이그레이션 + jOOQ 코드 생성
```bash
# Flyway 마이그레이션 → jOOQ 코드 생성 → 컴파일
./scripts/setup-db.sh

# 또는 개별 실행
./gradlew flywayMigrate   # DB 스키마 마이그레이션
./gradlew jooqCodegen     # 타입 안전한 DB 코드 생성
```

### 3) 빌드 & 테스트
```bash
# 환경 변수 자동 로드 후 실행 (권장)
./scripts/run-with-env.sh ./gradlew test
./scripts/run-with-env.sh ./gradlew run --args="validate-contracts src/main/resources/contracts/v1"
```

## Structure (RFC-V4-010: Orchestration-First Entry + Domain-Only Meaning)

```
src/main/kotlin/com/oliveyoung/ivmlite/
  shared/                      # 공통 코어 (결정성/에러/타입/공통 포트)
  package/                     # 도메인 그룹
    rawdata/                   # RawData 저장/조회 도메인
    changeset/                 # ChangeSet 빌더 도메인
    contracts/                 # Contract Registry 도메인
    slices/                    # Slice 저장/조회 도메인
    orchestration/             # Cross-domain 워크플로우 (SSOT)
      IngestWorkflow.kt        # 외부 진입점 (v0: 파일만)
      SlicingWorkflow.kt
      QueryViewWorkflow.kt
      # v1+: 복잡도 생기면 steps/, domain/, ports/ 추가
  tooling/                     # DX 도구 (개발/테스트 전용)
    - validate-contracts: 계약 파일 검증
    - 향후: codegen, simulate, diff, replay
  apps/                        # 트리거/엔트리포인트
    runtimeapi/                # (향후) HTTP/Worker 트리거
    opscli/                    # 운영 CLI 진입점
```

### 아키텍처 원칙 (RFC-V4-010)

**핵심 규칙**:
- **외부 진입은 orchestration만 호출** (apps는 트리거)
- **cross-domain workflow는 orchestration만 소유**
- **도메인 간 직접 import 금지** (ports 경유만 허용)
- **orchestration → orchestration 호출 금지** (깊이 제한)

### 도메인 구조
각 도메인은 **In-domain Hexagonal Architecture**를 따릅니다:
- `domain/`: 순수 의미 모델 + 불변식
- `ports/`: 외부 의존 계약 (인터페이스)
- `adapters/`: 인프라 구현 (DB, registry 등)
- `application/`: single-domain façade (선택, orchestration에서만 호출)

### Orchestration 구조 (v0 vs v1+)

**v0 (현재 단일 모듈)**:
- orchestration이 "도메인 정책"을 갖지 않음 (흐름/순서/실행 정책만)
- 파일만 배치하고 폴더 구조는 최소화
- 네이밍: `*Workflow.kt` (IngestWorkflow, SlicingWorkflow, QueryViewWorkflow)

**v1+ (필요시 확장)**:
- 복잡한 정책/규칙/상태 머신이 생기면 그때 hexagonal 구조 추가
- `steps/`: 내부 step 단위 (`*Step.kt`, `*Activity.kt` 등)
- `domain/`: 정책/규칙/상태 머신
- `ports/`: orchestration 전용 포트 (ObservabilityPort, TransactionCoordinatorPort 등)
- **원칙**: YAGNI (You Aren't Gonna Need It) - 실제 복잡도가 생기기 전까지는 추가하지 않기

### Tooling 역할
`tooling/`은 **개발자 경험(DX) 향상**을 위한 도구들을 제공합니다:
- **validate-contracts**: 계약 파일 검증 (YAML 파싱, 필수 필드 검증)
- **codegen** (계획): 계약에서 Kotlin SDK + JSON Schema 타입 자동 생성
- **simulate** (계획): 로컬에서 RawData → ChangeSet → Slices → View 시뮬레이션
- **diff** (계획): slice_hash/view_hash 비교
- **replay** (계획): ReplayRequestContract 기반 실행

Tooling은 런타임과 분리되어 있어, 개발/테스트 단계에서만 사용됩니다.

## Local Infrastructure (docker-compose)

로컬 개발 환경 인프라:

```bash
# 전체 설정 (PostgreSQL + DynamoDB + Kafka + Debezium)
./infra/setup-local.sh

# 또는 수동으로
docker-compose up -d
./infra/dynamodb/create-tables.sh
./infra/debezium/register-connector.sh
```

### 인프라 구성

| 서비스 | 용도 | 포트 |
|--------|------|------|
| **PostgreSQL** | 비즈니스 데이터 + Outbox (ACID) | 5432 |
| **DynamoDB (Remote)** | Schema Registry | (AWS) |
| **Kafka** (KRaft) | 이벤트 스트리밍 | 9094 |
| **Debezium** | PostgreSQL CDC → Kafka | 8083 |
| **Kafka UI** | 디버깅용 UI | 8080 |

### Outbox 패턴 (빵꾸 없이 이벤트 발행)

**PostgreSQL + Debezium** 조합으로 **Transactional Outbox 패턴** 구현:

```
[App] → BEGIN
         → INSERT raw_data (...)      # 비즈니스 데이터
         → INSERT outbox (...)        # 이벤트 (같은 트랜잭션!)
       → COMMIT

[Debezium] → outbox 테이블 CDC 감지 → Kafka 토픽으로 발행
```

**왜 PostgreSQL?**
- ACID 트랜잭션으로 "비즈니스 + 이벤트" 원자성 보장
- Debezium CDC가 업계 SOTA
- DynamoDB는 트랜잭션 제한 있음 (25개, 파티션 제약)

### Kafka Topics (자동 생성)

| Topic | 소스 | 설명 |
|-------|------|------|
| `ivm.events.raw_data` | Outbox | RawData 저장 이벤트 |
| `ivm.events.slice` | Outbox | Slice 생성 이벤트 |

## Database (Flyway + jOOQ)

**타입 안전한 DB 접근** - AI가 잘못된 필드명/테이블명 쓰면 **컴파일 에러**!

### 스택

| 도구 | 역할 |
|------|------|
| **Flyway** | DB 스키마 버전 관리 (마이그레이션) |
| **jOOQ** | DB 스키마 → Kotlin 코드 자동 생성 |
| **HikariCP** | 커넥션 풀 |

### 워크플로우

```
1. SQL 작성 (src/main/resources/db/migration/V*.sql)
       ↓
2. Flyway 마이그레이션 (./gradlew flywayMigrate)
       ↓
3. jOOQ 코드 생성 (./gradlew jooqCodegen)
       ↓
4. 컴파일 타임에 테이블/컬럼 검증!
```

### 마이그레이션 파일

```
src/main/resources/db/migration/
├── V001__init_extensions.sql    # uuid-ossp
├── V002__raw_data_table.sql     # RawData 테이블
├── V003__slices_table.sql       # Slices 테이블
├── V004__inverted_index_table.sql
├── V005__outbox_table.sql       # Transactional Outbox
└── V006__debezium_heartbeat.sql
```

### jOOQ 사용 예시

```kotlin
// 생성된 코드 import (빌드 후)
import com.oliveyoung.ivmlite.generated.jooq.Tables.RAW_DATA

// 타입 안전한 쿼리
dsl.selectFrom(RAW_DATA)
    .where(RAW_DATA.TENANT_ID.eq("tenant-1"))
    .and(RAW_DATA.ENTITY_KEY.eq("product-123"))
    .fetch()

// 잘못된 필드명 → 컴파일 에러!
// RAW_DATA.WRONG_FIELD  // ❌ 컴파일 에러
```

## Schema Registry (Contracts)

### v1 (개발/테스트)
- **어댑터**: `LocalYamlContractRegistryAdapter`
- **SSOT**: `src/main/resources/contracts/v1/*.yaml`
- **용도**: 개발/테스트/부트스트랩

### v2 (운영) - RFC-IMPL-007
- **어댑터**: `DynamoDBContractRegistryAdapter`
- **SSOT**: DynamoDB `ivm-lite-schema-registry-{env}` 테이블
- **용도**: 운영 환경

**포트 불변**: `ContractRegistryPort` 인터페이스는 동일 → DI 설정만 바꾸면 전환 완료

자세한 내용은 [RFC-IMPL-007](docs/rfc/rfcimpl007.md) 참조.

## DX
- `validate-contracts <dir>` : validates YAML contracts (syntax + required keys)

## Architecture Constraints (RFC-V4-010)

아키텍처 제약은 **ArchUnit 테스트 + Detekt**로 강제됩니다:

```bash
# 모든 체크 실행 (테스트 + 린트)
./gradlew checkAll

# 또는 개별 실행
./gradlew test --tests ArchitectureConstraintsTest
./gradlew detekt
```

### Semgrep (정적 분석 / 보안·버그 패턴)

```bash
# 사전: pip install semgrep 또는 brew install semgrep
./scripts/semgrep.sh        # 프로젝트 루트 스캔
./scripts/semgrep.sh src/   # src/ 만 스캔
./gradlew semgrep           # Gradle 태스크 (src/ 대상)
```

- **규칙셋**: `p/default`, `p/kotlin`, `p/security-audit` + `config/semgrep/semgrep.yml` (커스텀)
- **제외**: `.semgrepignore` (build/, .gradle/, generated, docs, infra 등)
- **커스텀 제약**: `printStackTrace` 금지, `Runtime.exec`/`ProcessBuilder` 금지 (로깅·명령 인젝션 방지)

### 강제되는 제약 (P0)

**ArchUnit 테스트**:
- ✅ 도메인 간 직접 import 금지 (ports 경유만)
- ✅ apps는 orchestration만 호출 (도메인 직접 호출 금지)
- ✅ orchestration → orchestration 호출 금지 (깊이 제한)
- ✅ orchestration은 ports를 통해서만 도메인 호출
- ✅ shared는 비즈니스 로직 금지
- ✅ tooling은 런타임 도메인 호출 금지
- ✅ orchestration 네이밍 규칙 (*Workflow)

**Detekt 린트**:
- ✅ 코드 품질 규칙 (복잡도, 네이밍, 라인 길이)
- ✅ `maxIssues: 50` (점진적으로 줄여나가기)

자세한 내용은 [RFC-V4-010](docs/rfc/rfc010.md) 참조.
