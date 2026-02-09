# ivm-lite (oliveyoung.com)

Contract-first deterministic data runtime:
RawData → Snapshot/ChangeSet → Slice → Virtual View → (v4.1) CDC → Sink

## Quickstart

### 0) AWS 자격 증명 설정 (자동 설정)

```bash
# .env 파일 자동 생성 및 환경 변수 로드
source scripts/load-env.sh

# 또는 환경 변수 자동 로드 후 애플리케이션 실행
./scripts/run-with-env.sh ./gradlew run
```

**자세한 설정 방법**: [AWS 자격 증명 설정 가이드](./docs/archive/aws-credentials-setup.md) 참고

### 1) 인프라 시작

```bash
# PostgreSQL + DynamoDB + Kafka + Debezium 시작
docker-compose up -d

# 또는 전체 설정 스크립트 (테이블 생성 + Debezium 등록 포함)
./infra/setup-local.sh
```

### 2) DB 마이그레이션 (선택적)

**참고**: jOOQ는 PostgreSQL 어댑터에서만 사용됩니다. DynamoDB나 InMemory 어댑터를 사용하는 경우 이 단계는 필요 없습니다.

```bash
# Flyway 마이그레이션 → jOOQ 코드 생성 → 컴파일
./scripts/setup-db.sh

# 또는 개별 실행
./gradlew flywayMigrate   # DB 스키마 마이그레이션
./gradlew jooqCodegen     # 타입 안전한 DB 코드 생성 (PostgreSQL 어댑터용)
```

### 3) 빌드 & 테스트

```bash
# 환경 변수 자동 로드 후 실행 (권장)
./scripts/run-with-env.sh ./gradlew test
./scripts/run-with-env.sh ./gradlew run --args="validate-contracts src/main/resources/contracts/v1"
```

### 4) 애플리케이션 실행

#### Runtime API (포트 8080)

```bash
# Just 사용 (권장)
just runtime

# 또는 Gradle 직접 사용
./gradlew run
```

#### Admin 앱 (포트 8081)

```bash
# Just 사용 (권장)
just admin-fast      # 빠른 빌드 후 실행
just admin           # 개발 모드
just admin-dev       # Hot Reload 모드

# 또는 Gradle 직접 사용
./gradlew fastAdmin
./gradlew runAdminDev
./gradlew --no-configuration-cache --continuous runAdminDev
```

#### Admin UI 개발 서버 (포트 3000)

```bash
# Just 사용 (권장)
just admin-ui-dev

# 또는 npm 직접 사용
cd admin-ui && npm run dev
```

**접속 주소**:
- Frontend 개발 서버: http://localhost:3000 (Vite HMR 자동 지원)
- Backend API: http://localhost:8081/api
- 프로덕션 빌드: http://localhost:8081/admin

**참고**: 
- Backend Hot Reload: 파일 변경 시 자동으로 재빌드 및 재시작됩니다
- Frontend Hot Reload: Vite가 자동으로 HMR(Hot Module Replacement) 지원
- 포트 충돌 시: `just kill-ports` 또는 `lsof -ti:8081 | xargs kill -9`

## 프로젝트 구조

```
src/main/kotlin/com/oliveyoung/ivmlite/
├── sdk/                            # SDK 레이어 (외부 개발자용)
│   ├── Ivm.kt                     # SDK 진입점
│   ├── client/                    # HTTP 클라이언트 (IvmClient)
│   ├── dsl/                       # DSL 빌더 (Product, Brand, Category 등)
│   ├── execution/                 # DeployExecutor, DeployStateMachine
│   ├── model/                     # DeployJob, DeployResult, DeployPlan 등
│   └── schema/                    # ViewRef, EntityRef (코드젠)
│
├── pkg/                           # 핵심 비즈니스 로직 (도메인 패키지)
│   ├── orchestration/             # 워크플로우 엔진 (Cross-domain)
│   │   └── application/
│   │       ├── IngestWorkflow.kt  # RawData 저장 워크플로우
│   │       ├── SlicingWorkflow.kt # Slice 생성 워크플로우
│   │       ├── QueryViewWorkflow.kt # View 조회 워크플로우
│   │       └── ShipWorkflow.kt    # Sink 전송 워크플로우
│   │
│   ├── slices/
│   │   ├── domain/
│   │   │   └── SlicingEngine.kt   # 슬라이싱 엔진 (RuleSet 기반)
│   │   ├── ports/                 # SliceRepositoryPort, SlicingEnginePort
│   │   └── adapters/              # Jooq, DynamoDB, InMemory 어댑터
│   │
│   ├── rawdata/                   # RawData 도메인
│   ├── contracts/                 # Contract Registry 도메인
│   ├── views/                     # View 도메인
│   ├── sinks/                     # Sink 도메인
│   ├── changeset/                 # ChangeSet 빌더 도메인
│   ├── alerts/                    # 알림 엔진
│   ├── backfill/                  # 백필 작업
│   └── webhooks/                  # Webhook 관리
│
├── apps/                          # 애플리케이션 레이어
│   ├── runtimeapi/                # Runtime API (포트 8080)
│   │   ├── routes/                # HTTP 라우트
│   │   └── wiring/                # DI 모듈 (Koin)
│   │
│   ├── admin/                     # Admin API (포트 8081)
│   │   ├── routes/                # Admin API 라우트
│   │   ├── application/          # Admin 서비스 (Dashboard, Pipeline 등)
│   │   └── wiring/                # Admin DI 모듈
│   │
│   └── opscli/                    # CLI 도구
│
├── shared/                        # 공통 코어
│   ├── domain/                    # 결정성, 에러, 타입
│   ├── ports/                     # 공통 포트
│   └── config/                    # 설정
│
└── tooling/                       # DX 도구 (개발/테스트 전용)
    ├── application/               # validate-contracts 등
    └── codegen/                   # 코드 생성 도구

admin-ui/src/                       # Admin UI (React + TypeScript)
├── app/                           # 앱 설정, 라우팅
├── features/                      # 기능별 모듈
│   ├── dashboard/                 # 대시보드
│   ├── contracts/                 # Contract 관리
│   ├── pipeline/                  # 파이프라인 시각화
│   ├── workflow/                  # 워크플로우 캔버스
│   ├── outbox/                    # Outbox 관리
│   ├── explorer/                  # 데이터 탐색기
│   ├── playground/                # 플레이그라운드
│   └── ...
├── shared/                        # 공통 컴포넌트
└── widgets/                       # 레이아웃 위젯
```

## 핵심 컴포넌트

### SDK (Software Development Kit)

**위치**: `src/main/kotlin/com/oliveyoung/ivmlite/sdk/`

**역할**: 외부 개발자가 사용하는 타입 안전한 API

**주요 기능**:
- **Write API**: `Ivm.product { ... }.deploy()` - 엔티티 배포
- **Read API**: `Ivm.query(Views.Product.Pdp).key("SKU-001").get()` - 타입 안전한 조회
- **Deploy State Machine**: 배포 상태 관리 (COMPILE → SHIP → CUTOVER)
- **Async Deploy**: 비동기 배포 및 상태 조회

**사용 예시**:
```kotlin
// Write
Ivm.product {
    tenantId = "oliveyoung"
    sku = "SKU-001"
    name = "비타민C"
    price = 15000
}.deploy()

// Read (타입 안전)
val view = Ivm.query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
```

### 엔진 (Engine Layer)

#### 1. SlicingEngine (슬라이싱 엔진)

**위치**: `pkg/slices/domain/SlicingEngine.kt`

**역할**: RawData → Slice 변환 (RuleSet Contract 기반)
- RuleSet 기반 슬라이싱
- JOIN 실행 (Light JOIN)
- Inverted Index 생성
- 결정성 보장 (동일 입력 → 동일 출력)

#### 2. QueryViewWorkflow (뷰 조회 엔진)

**위치**: `pkg/orchestration/application/QueryViewWorkflow.kt`

**역할**: ViewDefinition 기반 Slice 조합
- 여러 Slice를 조합하여 View 생성
- MissingPolicy 처리 (FAIL_CLOSED / PARTIAL_ALLOWED)
- Range Query, Count Query, Latest Query 지원

#### 3. Workflow 엔진들

| Workflow | 역할 | 위치 |
|----------|------|------|
| `IngestWorkflow` | RawData 저장 + Outbox 이벤트 생성 | `pkg/orchestration/application/IngestWorkflow.kt` |
| `SlicingWorkflow` | SlicingEngine으로 Slice 생성 (FULL/INCREMENTAL) | `pkg/orchestration/application/SlicingWorkflow.kt` |
| `QueryViewWorkflow` | ViewDefinition 기반 Slice 조회 | `pkg/orchestration/application/QueryViewWorkflow.kt` |
| `ShipWorkflow` | SinkRule 기반 외부 시스템 전송 | `pkg/orchestration/application/ShipWorkflow.kt` |

### Admin 기능

**위치**: `apps/admin/` (Backend), `admin-ui/` (Frontend)

**주요 기능**:

1. **Dashboard** (`/dashboard`)
   - Worker 상태 실시간 모니터링
   - Outbox 통계 (Pending, Processing, Failed, Processed)
   - 데이터 파이프라인 개요
   - Slice 타입별 통계

2. **Contracts 관리** (`/contracts`)
   - Entity Schema, RuleSet, ViewDefinition, SinkRule 조회
   - YAML 원문 및 파싱된 데이터 확인
   - Contract 의존성 그래프 시각화

3. **Pipeline 시각화** (`/pipeline`)
   - 데이터 흐름 시각화 (Raw → Slice → View → Sink)
   - Entity별 흐름 추적
   - 단계별 통계 확인

4. **Workflow Canvas** (`/workflow`)
   - 파이프라인 시각화 및 편집
   - Contract 간 의존성 그래프

5. **Outbox 관리** (`/outbox`)
   - 최근 처리된 작업 조회
   - 실패한 작업 조회 및 재시도
   - DLQ (Dead Letter Queue) 관리
   - Stale 엔트리 관리

6. **Data Explorer** (`/explorer`)
   - RawData, Slice, View 조회
   - 버전 히스토리 탐색
   - 데이터 비교 및 차이 확인

7. **Playground** (`/playground`)
   - Contract 시뮬레이션
   - 데이터 변환 테스트

8. **Webhooks** (`/webhooks`)
   - Webhook 등록 및 관리
   - 전송 이력 조회

9. **Alerts** (`/alerts`)
   - 알림 규칙 관리
   - 알림 이력 조회

10. **Backfill** (`/backfill`)
    - 재처리 작업 관리
    - 백필 작업 실행 및 모니터링

## 아키텍처 원칙 (RFC-V4-010)

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

### Repository 어댑터 (선택 가능)

프로젝트는 **Port/Adapter 패턴**을 사용하여 다양한 저장소를 지원합니다:

| 도메인 | PostgreSQL (jOOQ) | DynamoDB | InMemory |
|--------|-------------------|----------|----------|
| RawData | `JooqRawDataRepository` | `DynamoDbRawDataRepository` | `InMemoryRawDataRepository` |
| Slice | `JooqSliceRepository` | `DynamoDbSliceRepository` | `InMemorySliceRepository` |
| Contract | - | `DynamoDBContractRegistryAdapter` | `LocalYamlContractRegistryAdapter` |

**jOOQ는 선택적**: PostgreSQL 어댑터에서만 사용되며, DynamoDB나 InMemory 어댑터를 사용하는 경우 jOOQ 코드 생성이 필요 없습니다.

**DI 설정**: `apps/runtimeapi/wiring/AdapterModule.kt`에서 어댑터를 선택합니다.

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

**참고**: jOOQ는 PostgreSQL 어댑터에서만 사용됩니다. DynamoDB나 InMemory 어댑터를 사용하는 경우 이 섹션은 건너뛸 수 있습니다.

### 스택

| 도구 | 역할 |
|------|------|
| **Flyway** | DB 스키마 버전 관리 (마이그레이션) |
| **jOOQ** | DB 스키마 → Kotlin 코드 자동 생성 (PostgreSQL 어댑터용) |
| **HikariCP** | 커넥션 풀 |

### 워크플로우

```
1. SQL 작성 (src/main/resources/db/migration/V*.sql)
       ↓
2. Flyway 마이그레이션 (./gradlew flywayMigrate)
       ↓
3. jOOQ 코드 생성 (./gradlew jooqCodegen) - PostgreSQL 어댑터 사용 시만 필요
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

### jOOQ 사용 예시 (PostgreSQL 어댑터)

```kotlin
// 생성된 코드 import (빌드 후)
import com.oliveyoung.ivmlite.generated.jooq.Tables.RAW_DATA

// 타입 안전한 쿼리
dsl.selectFrom(RAW_DATA)
    .where(RAW_DATA.TENANT_ID.eq("tenant-1"))
    .and(RAW_DATA.ENTITY_KEY.eq("product-123"))
    .fetch()

// 잘못된 필드명 → 컴파일 에러!
// RAW_DATA.WRONG_FIELD  // 컴파일 에러
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

## 개발 도구

### Just 명령어 러너 (권장)

`just` 명령어 러너를 사용하면 더 간편합니다 (`brew install just` 또는 `cargo install just`):

```bash
just admin-dev      # Admin Backend 개발 모드 (Hot Reload)
just admin-ui-dev   # Admin Frontend 개발 모드
just dev            # 전체 개발 환경 가이드
just --list         # 모든 명령어 보기
```

### 테스트

```bash
# 기본 단위 테스트
./gradlew test

# 빠른 단위 테스트 (병렬 실행)
./gradlew unitTest

# 통합 테스트 (Docker 필요)
./gradlew integrationTest

# 특정 패키지 테스트
./gradlew testPackage -Dpkg=slices
./gradlew testPackage -Dpkg=contracts
./gradlew testPackage -Dpkg=orchestration

# 전체 검사 (테스트 + 린트)
./gradlew checkAll
```

### DX 도구

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
- 도메인 간 직접 import 금지 (ports 경유만)
- apps는 orchestration만 호출 (도메인 직접 호출 금지)
- orchestration → orchestration 호출 금지 (깊이 제한)
- orchestration은 ports를 통해서만 도메인 호출
- shared는 비즈니스 로직 금지
- tooling은 런타임 도메인 호출 금지
- orchestration 네이밍 규칙 (*Workflow)

**Detekt 린트**:
- 코드 품질 규칙 (복잡도, 네이밍, 라인 길이)
- `maxIssues: 50` (점진적으로 줄여나가기)

자세한 내용은 [RFC-V4-010](docs/rfc/rfc010.md) 참조.

## 환경변수 설정

**.env 파일에 DB 접속 정보가 있습니다. jOOQ 코드 생성, 테스트 실행 시 반드시 로드하세요.**

```bash
# .env 로드 후 Gradle 실행
source .env && ./gradlew jooqCodegen
source .env && ./gradlew test

# 또는 export로 직접 설정
export DB_URL="jdbc:postgresql://..."
export DB_USER="postgres"
export DB_PASSWORD="..."
```

.env 파일 주요 변수:
- `DB_URL`: PostgreSQL JDBC URL
- `DB_USER`: DB 사용자
- `DB_PASSWORD`: DB 비밀번호
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`: DynamoDB 접근용
- `AWS_REGION`: AWS 리전 (기본값: ap-northeast-2)
- `DYNAMODB_TABLE`: DynamoDB 테이블명
- `ADMIN_PORT`: Admin 앱 포트 (기본값: 8081)

## Git Hooks 설정

프로젝트에 Git hooks가 설정되어 있어 커밋/푸시 전 자동으로 검사를 수행합니다.

### 설치

```bash
# Git hooks 설치 (최초 1회)
./scripts/install-git-hooks.sh
```

### Pre-commit Hook

커밋 전 자동 실행:
- Backend: 단위 테스트 (`./gradlew unitTest`), 린트 (`./gradlew detekt`)
- Frontend: admin-ui 파일 변경 시 타입 체크 및 린트

### Pre-push Hook

푸시 전 자동 실행:
- Backend: 전체 검사 (`./gradlew checkAll`)
- Frontend: 타입 체크 및 린트

### Hooks 건너뛰기 (비권장)

필요시 `--no-verify` 플래그로 건너뛸 수 있습니다:
```bash
git commit --no-verify
git push --no-verify
```

## 주의사항

1. **환경변수**: .env 파일에서 로드 (source .env)
2. Configuration Cache: 활성화되어 있음. 환경변수는 System.getenv() 대신 providers.environmentVariable() 사용 권장
3. 통합 테스트: Docker가 필요함 (integrationTest 태스크)
4. jOOQ 코드: PostgreSQL 어댑터 사용 시에만 필요 (`./gradlew jooqCodegen`, DB 연결 필요, .env 로드 필수)
5. 계약 파일: src/main/resources/contracts/v1/ 에 YAML로 정의
6. 보안: AWS 자격 증명은 환경 변수로 관리하며, Git에 커밋하지 않도록 주의
7. Git Hooks: 최초 클론 후 `./scripts/install-git-hooks.sh` 실행 권장

## 자주 사용하는 워크플로우

### 개발 시작 (전체 환경)

```bash
# Just 사용 (권장)
just dev  # 실행 가이드 표시

# 터미널 1: Backend (Hot Reload)
just admin-dev

# 터미널 2: Frontend (Hot Reload)
just admin-ui-dev
```

### 코드 수정 후 확인

```bash
./gradlew unitTest testPackage -Dpkg=수정한패키지명
```

### PR 전 체크

```bash
./gradlew checkAll
cd admin-ui && npm run lint && npm run typecheck
```
