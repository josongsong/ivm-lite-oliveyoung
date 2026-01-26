# ivm-lite Implementation Roadmap (RFC-IMPL Master Index)

Status: **Phase A/B/C/D Complete ✅ → Phase E (Fluent SDK DX) Ready**
Last Updated: 2026-01-25
Approach: **Interface First → Implementation → Core Business Logic → SDK DX**

---

## ✅ Gap Analysis (RFC vs 실제 구현) - ALL RESOLVED

### ✅ 완료 (v1 인프라/스캐폴딩 + 핵심 비즈니스 로직)
- Gradle Wrapper, CI Gates (checkAll, detekt, ArchUnit)
- Contract YAML 6개 + LocalYaml/DynamoDB 어댑터
- IngestWorkflow (canonicalize + hash + Outbox)
- SlicingWorkflow (RuleSet 기반 슬라이싱 + JOIN + Index)
- QueryViewWorkflow (ViewDefinition 기반 정책 적용)
- Outbox + PollingWorker (INCREMENTAL 자동 선택)
- DynamoDB Adapter (캐싱 + checksum 검증)
- HealthCheckable 전체 커버리지

### ✅ RFC-V4 핵심 비즈니스 로직 (2026-01-25 완료)
| RFC | 항목 | 상태 |
|-----|------|--------|
| RFC-001/003 | SliceRecord.tombstone | ✅ Complete |
| RFC-001/003 | JoinExecutor DI wiring | ✅ GAP-A |
| RFC-001/003 | RuleSet slices[].joins 파싱 | ✅ GAP-B |
| RFC-001 | Inverted Index 생성 | ✅ GAP-C |
| RFC-001/003 | ImpactMap 계산 | ✅ Complete |
| RFC-001 | INCREMENTAL slicing (executeAuto) | ✅ GAP-F |
| RFC-003 | ViewDefinition + Policy (v2 API) | ✅ GAP-D |
| RFC-003 | ContractStatusGate | ✅ Complete |
| IMPL-007 | DynamoDB 캐싱/checksum | ✅ Complete |
| IMPL-009 | Readiness HealthCheckable | ✅ GAP-G |
| IMPL-010 | JooqInvertedIndexRepository | ✅ GAP-E |

**상세: [RFC-IMPL-010](./rfcimpl010.md) | [Gap Implementation Plan](./rfcimpl010-gap-impl-plan.md)**

### ⬜ Phase E: Fluent SDK DX (RFC-IMPL-011)
| RFC | 항목 | 상태 |
|-----|------|--------|
| RFC-008 | Ivm.client().ingest().product { } | ⬜ E-1 |
| RFC-008 | .deploy { compile.sync(); ship.async {} } | ⬜ E-2 |
| RFC-008 | opensearch(); personalize() Sink DSL | ⬜ E-3 |
| RFC-008 | deployNow / deployQueued Shortcuts | ⬜ E-4 |
| RFC-008 | DeployJob + StateMachine | ⬜ E-5 |
| RFC-009 | compile { targets { searchDoc() } } | ⬜ E-6 |
| RFC-003 | Contract Codegen | ⬜ E-7 |

**상세: [RFC-IMPL-011](./rfcimpl011.md) | [Implementation Plan](./rfcimpl011-impl-plan.md)**

---

## 🎯 개발 방법론

### Interface First 원칙
1. **Phase A**: 모든 인터페이스, Enum, 도메인 모델 먼저 정의
2. **Phase B**: 실제 구현 (InMemory → jOOQ → Production)

### v1 아키텍처 (Polling 방식)
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Ingest API    │ ──▶ │  PostgreSQL     │ ◀── │  Polling Worker │
│   (Ktor)        │     │  (outbox 테이블) │     │  (Coroutine)    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

> **v2에서 Kafka/Debezium으로 전환** (포트 동일, 어댑터만 교체)

---

## 📋 Phase A: Interface & Scaffold (인터페이스 정의)

### A-1. Enum & Constants ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 1 | `shared/domain/types/SliceType.kt` | CORE, JOINED, DERIVED | ✅ |
| 2 | `shared/domain/types/AggregateType.kt` | RAW_DATA, SLICE, CHANGESET | ✅ |
| 3 | `shared/domain/types/OutboxStatus.kt` | PENDING, PROCESSED, FAILED | ✅ |

### A-2. Domain Models (보완) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 4 | `pkg/rawdata/domain/RawDataRecord.kt` | 필드 정리/주석 | ✅ 있음 |
| 5 | `pkg/rawdata/domain/OutboxEntry.kt` | Polling용 Outbox | ✅ |
| 6 | `pkg/slices/domain/SliceRecord.kt` | 필드 정리/주석 | ✅ 있음 |
| 7 | `pkg/changeset/domain/ChangeSet.kt` | 필드 정리/주석 | ✅ 있음 |
| 8 | `pkg/changeset/domain/ImpactMap.kt` | v1.1용 Impact 정의 | ⏳ v1.1 |

### A-3. Error Hierarchy (확장) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 9 | `shared/domain/errors/DomainError.kt` | 에러 타입 체계화 | ✅ |

```kotlin
sealed class DomainError {
    // 계약/검증
    data class ContractError(val msg: String)
    data class ValidationError(val field: String, val msg: String)
    
    // 저장소
    data class NotFoundError(val entity: String, val key: String)
    data class IdempotencyViolation(val msg: String)
    data class StorageError(val msg: String)
    
    // 외부 서비스
    data class ExternalServiceError(val service: String, val msg: String)
}
```

### A-4. Port Interfaces (완성) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 10 | `pkg/rawdata/ports/RawDataRepositoryPort.kt` | 기존 + 확장 | ✅ 있음 |
| 11 | `pkg/rawdata/ports/OutboxRepositoryPort.kt` | **Polling용** | ✅ |
| 12 | `pkg/slices/ports/SliceRepositoryPort.kt` | 기존 | ✅ 있음 |
| 13 | `pkg/slices/ports/InvertedIndexRepositoryPort.kt` | 기존 | ✅ 있음 |
| 14 | `pkg/changeset/ports/ChangeSetRepositoryPort.kt` | v1.1용 | ✅ |
| 15 | `pkg/contracts/ports/ContractRegistryPort.kt` | 메서드 확장 | ✅ 있음 |

### A-5. API DTOs (정리) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 16 | `apps/runtimeapi/dto/Requests.kt` | Ingest/Slice/Query Request | ✅ |
| 17 | `apps/runtimeapi/dto/Responses.kt` | 성공/에러 Response + DomainError 통합 | ✅ |

### A-6. 기존 도메인에 Enum 적용 ✅
| # | 파일 | 변경 | 상태 |
|---|------|------|------|
| 18 | `pkg/slices/domain/SliceRecord.kt` | `sliceType: String` → `SliceType` | ✅ |
| 19 | `pkg/slices/adapters/*` | SliceType 사용하도록 수정 | ✅ |
| 20 | `pkg/orchestration/application/SlicingWorkflow.kt` | SliceType 사용 | ✅ |

---

## 📋 Phase B: Implementation (구현)

### B-0. Workflow 통합 (Transactional Outbox) ✅
> ✅ **완료**: IngestWorkflow에서 RawData + Outbox를 같이 저장

| # | 파일 | 변경 | 상태 |
|---|------|------|------|
| 1 | `pkg/orchestration/application/IngestWorkflow.kt` | OutboxRepositoryPort 주입, insert 호출 | ✅ |
| 2 | `apps/runtimeapi/wiring/WorkflowModule.kt` | OutboxRepositoryPort 주입 설정 | ✅ |
| 3 | 테스트 | Ingest 시 Outbox에 이벤트 저장 검증 | ✅ |


### B-1. InMemory Adapters (v1 개발/테스트) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 1 | `pkg/rawdata/adapters/InMemoryRawDataRepository.kt` | 기존 | ✅ 완료 |
| 2 | `pkg/rawdata/adapters/InMemoryOutboxRepository.kt` | Polling용 | ✅ |
| 3 | `pkg/slices/adapters/InMemorySliceRepository.kt` | 기존 | ✅ 완료 |
| 4 | `pkg/changeset/adapters/InMemoryChangeSetRepository.kt` | v1.1용 | ✅ |

### B-2. Polling Worker ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 5 | `apps/worker/OutboxPollingWorker.kt` | Coroutine 기반 Polling | ✅ |
| 6 | `apps/runtimeapi/wiring/WorkerModule.kt` | Worker Koin 모듈 | ✅ |

### B-3. jOOQ Adapters (PostgreSQL) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 7 | `pkg/rawdata/adapters/JooqRawDataRepository.kt` | DB 저장 | ✅ |
| 8 | `pkg/rawdata/adapters/JooqOutboxRepository.kt` | Outbox 저장 | ✅ |
| 9 | `pkg/slices/adapters/JooqSliceRepository.kt` | Slice 저장 | ✅ |
| 10 | `apps/runtimeapi/wiring/AdapterModule.kt` | jooqAdapterModule 등록 | ✅ |

### B-4. Testcontainers Integration Tests ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 11 | `integration/PostgresTestContainer.kt` | Testcontainers 설정 | ✅ |
| 12 | `integration/JooqRawDataRepositoryIntegrationTest.kt` | RawData 통합테스트 | ✅ |
| 13 | `integration/JooqOutboxRepositoryIntegrationTest.kt` | Outbox 통합테스트 | ✅ |
| 14 | `integration/JooqSliceRepositoryIntegrationTest.kt` | Slice 통합테스트 | ✅ |

### B-5. DynamoDB Adapter (v2 운영) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 15 | `pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt` | 스키마 레지스트리 | ✅ |
| 16 | `apps/runtimeapi/wiring/AdapterModule.kt` | dynamodbContractModule, productionAdapterModule | ✅ |
| 17 | `pkg/contracts/DynamoDBContractRegistryAdapterTest.kt` | MockK 기반 단위테스트 | ✅ |

---

## 📋 Phase C: RFC-IMPL 마무리 (경미한 누락)

### C-1. DynamoDB 캐싱 (IMPL-007) ⏳
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 1 | `shared/ports/ContractCache.kt` | 캐시 인터페이스 | ⏳ |
| 2 | `shared/adapters/InMemoryContractCache.kt` | LRU + TTL 캐시 | ⏳ |
| 3 | `pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt` | cache 파라미터 추가 | ⏳ |

### C-2. DynamoDB checksum 검증 (IMPL-007) ⏳
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 4 | `shared/domain/errors/DomainError.kt` | ContractIntegrityError 추가 | ⏳ |
| 5 | `pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt` | verifyChecksum 메서드 | ⏳ |

### C-3. Readiness 동적 wiring (IMPL-009 P2) ⏳
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 6 | `shared/ports/HealthCheckable.kt` | 헬스체크 인터페이스 | ⏳ |
| 7 | `apps/runtimeapi/routes/HealthRoutes.kt` | 동적 어댑터 기준 체크 | ⏳ |

---

## 📋 Phase D: Core Business Logic (RFC-V4 핵심)

> **상세: [RFC-IMPL-010](./rfcimpl010.md)**

### D-1. SliceRecord.tombstone (P0) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 1 | `pkg/slices/domain/SliceRecord.kt` | tombstone 필드 추가 | ✅ |
| 2 | `pkg/slices/domain/Tombstone.kt` | Tombstone, DeleteReason | ✅ |
| 3 | `db/migration/V008__slice_tombstone.sql` | DB 마이그레이션 | ✅ |

### D-2. RuleSet 도메인 + 로딩 (P0) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 4 | `pkg/contracts/domain/RuleSetContract.kt` | RuleSet 도메인 모델 | ✅ |
| 5 | `pkg/contracts/ports/ContractRegistryPort.kt` | loadRuleSetContract 추가 | ✅ |
| 6 | `resources/contracts/v1/ruleset.v1.yaml` | RuleSet 계약 파일 | ✅ |

### D-3. SlicingEngine (P0) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 7 | `pkg/slices/domain/SlicingEngine.kt` | RuleSet 기반 슬라이싱 | ✅ |
| 8 | `pkg/orchestration/application/SlicingWorkflow.kt` | SlicingEngine 연동 | ✅ |

### D-4. JoinSpec 실행 (P0) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 9 | `pkg/slices/domain/JoinExecutor.kt` | Light JOIN 실행 | ✅ |
| 10 | `pkg/slices/domain/SlicingEngine.kt` | JoinExecutor 연동 | ✅ |

### D-5. ViewDefinition + Policy (P1) ⏳
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 11 | `pkg/contracts/domain/ViewDefinitionContract.kt` | ViewDefinition 도메인 | ⏳ |
| 12 | `pkg/contracts/domain/MissingPolicy.kt` | MissingPolicy, PartialPolicy | ⏳ |
| 13 | `resources/contracts/v1/view-definition.v1.yaml` | ViewDefinition 계약 | ⏳ |
| 14 | `pkg/orchestration/application/QueryViewWorkflow.kt` | Policy 적용 | ⏳ |

### D-6. ContractStatusGate (P1) ⏳
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 15 | `pkg/contracts/domain/ContractStatusGate.kt` | 상태 검증 게이트 | ⏳ |
| 16 | `pkg/contracts/adapters/GatedContractRegistryAdapter.kt` | 게이트 래퍼 | ⏳ |

### D-7. ImpactMap 계산 (P1) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 17 | `pkg/changeset/domain/ImpactCalculator.kt` | ImpactMap 계산 서비스 | ✅ |

### D-8. INCREMENTAL Slicing (P1) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 18 | `pkg/slices/ports/SliceRepositoryPort.kt` | getByVersion() 추가 | ✅ |
| 19 | `pkg/slices/domain/SlicingEngine.kt` | slicePartial() 추가 | ✅ |
| 20 | `pkg/orchestration/application/SlicingWorkflow.kt` | executeIncremental() 구현 | ✅ |
| 21 | 테스트 | FULL == INCREMENTAL 동치 속성 테스트 | ✅ |
| 22 | 테스트 | 엣지/코너 케이스 전수 테스트 | ✅ |

### D-9. Inverted Index 빌더 (P1) ✅
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 23 | `pkg/slices/domain/InvertedIndexBuilder.kt` | Index 생성 서비스 | ✅ |
| 24 | `pkg/slices/domain/SlicingEngine.kt` | Index 동시 생성 연동 | ✅ |

---

## 📋 Phase E: Fluent SDK DX (RFC-IMPL-011)

> **상세: [RFC-IMPL-011](./rfcimpl011.md) | [Implementation Plan](./rfcimpl011-impl-plan.md)**

### E-1. Core SDK Infrastructure (P0) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 1 | `sdk/dsl/markers/IvmDslMarker.kt` | @DslMarker 정의 | ⬜ |
| 2 | `sdk/client/IvmClientConfig.kt` | 클라이언트 설정 | ⬜ |
| 3 | `sdk/client/IvmClient.kt` | Ivm.client() 진입점 | ⬜ |
| 4 | `sdk/dsl/ingest/IngestContext.kt` | .ingest() DSL | ⬜ |
| 5 | `sdk/dsl/entity/ProductDsl.kt` | .product { } Builder | ⬜ |
| 6 | `sdk/dsl/deploy/DeployableContext.kt` | Entity → Deploy 체이닝 | ⬜ |

### E-2. Deploy Orchestration DSL (P0) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 7 | `sdk/model/CompileMode.kt` | Sync, Async, SyncWithTargets | ⬜ |
| 8 | `sdk/model/ShipMode.kt` | Sync, Async | ⬜ |
| 9 | `sdk/model/CutoverMode.kt` | Ready, Done | ⬜ |
| 10 | `sdk/dsl/deploy/DeployBuilder.kt` | .deploy { } 빌더 | ⬜ |
| 11 | `sdk/dsl/deploy/CompileAccessor.kt` | compile.sync/async | ⬜ |
| 12 | `sdk/dsl/deploy/ShipAccessor.kt` | ship.sync/async | ⬜ |
| 13 | `sdk/validation/AxisValidator.kt` | 축 조합 검증 | ⬜ |

### E-3. Sink DSL (P0) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 14 | `sdk/dsl/sink/SinkBuilder.kt` | Sink 컨테이너 | ⬜ |
| 15 | `sdk/dsl/sink/OpenSearchBuilder.kt` | opensearch { } | ⬜ |
| 16 | `sdk/dsl/sink/PersonalizeBuilder.kt` | personalize { } | ⬜ |

### E-4. Shortcut APIs (P1) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 17 | `sdk/dsl/shortcuts/DeployShortcuts.kt` | deployNow, deployQueued 등 | ⬜ |

### E-5. Async & Status (P1) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 18 | `sdk/model/DeployState.kt` | 상태 머신 상태 | ⬜ |
| 19 | `sdk/execution/StateMachine.kt` | 상태 전이 로직 | ⬜ |
| 20 | `sdk/client/DeployStatusApi.kt` | deploy.status() | ⬜ |

### E-6. Compiler Targets - RFC-009 (P1) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 21 | `sdk/dsl/deploy/TargetsBuilder.kt` | targets { searchDoc() } | ⬜ |
| 22 | `sdk/model/DeployPlan.kt` | Plan 설명 모델 | ⬜ |
| 23 | `sdk/client/PlanExplainApi.kt` | explainLastPlan() | ⬜ |

### E-7. Contract Codegen (P2) ⬜
| # | 파일 | 설명 | 상태 |
|---|------|------|------|
| 24 | `codegen/EntityDslGenerator.kt` | RuleSet → EntityDsl | ⬜ |
| 25 | `codegen/SinkDslGenerator.kt` | SinkRule → SinkDsl | ⬜ |

### Golden Tests (RFC 예시 전체) ⬜
- [ ] RFC-008 9-1: `Ivm.client().ingest().product { sku(); name(); price() }`
- [ ] RFC-008 10-1: `.deploy { ship.async { opensearch() } }`
- [ ] RFC-008 10-2: `.deploy { compile.sync(); ship.sync { } }`
- [ ] RFC-008 10-3: `.deployAsync { compile.async(); ship.async { } }`
- [ ] RFC-008 11-1: `.deployNow { opensearch() }`
- [ ] RFC-008 11-2: `.deployNowAndShipNow { opensearch() }`
- [ ] RFC-008 11-3: `.deployQueued { opensearch() }`
- [ ] RFC-009 11-1: `.deploy { compile { targets { searchDoc() } } }`
- [ ] RFC-009 11-2: `explainLastPlan(deployId)`

---

## ✅ 완료 현황

### Phase 0: Foundation ✅
- [x] Gradle Wrapper
- [x] build.gradle.kts (모든 의존성)
- [x] ArchUnit + Detekt 설정
- [x] Ktor + Koin + Hoplite 설정
- [x] Contract YAML 로딩
- [x] 19개 테스트 통과

### Phase 1: Core Workflows ✅
- [x] IngestWorkflow + 테스트 6개
- [x] SlicingWorkflow + 테스트 3개
- [x] QueryViewWorkflow + 테스트 3개
- [x] Ktor 라우트 (`/api/v1/ingest`, `/slice`, `/query`)

### Phase A: Interface ✅
- [x] A-1: Enum 정의 (SliceType, AggregateType, OutboxStatus)
- [x] A-2: OutboxEntry 도메인
- [x] A-3: DomainError 확장
- [x] A-4: Port Interfaces
- [x] A-5: API DTO 정리
- [x] A-6: 기존 도메인에 Enum 적용 ✅

### Phase B: Implementation ✅
- [x] B-0: Workflow 통합 (Outbox 주입) ✅
- [x] B-1: InMemory Adapters ✅
- [x] B-2: Polling Worker ✅
- [x] B-3: jOOQ Adapters ✅
- [x] B-4: Testcontainers Integration Tests ✅ (28개)
- [x] B-5: DynamoDB Adapter ✅

### Phase D: Core Business Logic (RFC-V4) ✅
- [x] D-1: SliceRecord.tombstone ✅
- [x] D-2: RuleSet 도메인 + 로딩 ✅
- [x] D-3: SlicingEngine (RuleSet 기반) ✅
- [x] D-4: JoinSpec 실행 (Light JOIN) ✅
- [x] D-7: ImpactMap 계산 ✅
- [x] D-8: INCREMENTAL Slicing ✅
  - [x] SliceRepositoryPort.getByVersion()
  - [x] SlicingEngine.slicePartial()
  - [x] SlicingWorkflow.executeIncremental()
  - [x] FULL == INCREMENTAL 동치 속성 테스트
  - [x] 엣지/코너 케이스 전수 테스트 (8개)
- [x] D-9: Inverted Index 빌더 ✅

### Phase E: Fluent SDK DX (RFC-IMPL-011) ⬜
- [ ] E-1: Core SDK (IvmClient, IngestContext, ProductDsl)
- [ ] E-2: Deploy DSL (compile/ship/cutover)
- [ ] E-3: Sink DSL (opensearch/personalize)
- [ ] E-4: Shortcut APIs (deployNow/deployQueued)
- [ ] E-5: Async & Status (DeployJob, StateMachine)
- [ ] E-6: Compiler Targets (RFC-009)
- [ ] E-7: Contract Codegen

---

## 📁 Directory Structure

```
ivm-lite/
├── src/main/kotlin/com/oliveyoung/ivmlite/
│   ├── shared/
│   │   └── domain/
│   │       ├── types/
│   │       │   ├── CoreTypes.kt       # TenantId, EntityKey, SemVer
│   │       │   ├── SliceType.kt       # ✅
│   │       │   ├── AggregateType.kt   # ✅
│   │       │   └── OutboxStatus.kt    # ✅
│   │       ├── errors/
│   │       │   └── DomainError.kt     # ✅
│   │       └── determinism/
│   │           ├── CanonicalJson.kt
│   │           └── Hashing.kt
│   │
│   ├── sdk/                           # ⬜ Phase E (RFC-IMPL-011)
│   │   ├── client/
│   │   │   ├── Ivm.kt                 # ⬜ object Ivm { fun client() }
│   │   │   ├── IvmClient.kt           # ⬜
│   │   │   └── DeployStatusApi.kt     # ⬜
│   │   ├── dsl/
│   │   │   ├── markers/IvmDslMarker.kt # ⬜
│   │   │   ├── ingest/IngestContext.kt # ⬜
│   │   │   ├── entity/ProductDsl.kt    # ⬜
│   │   │   ├── deploy/DeployBuilder.kt # ⬜
│   │   │   └── sink/SinkBuilder.kt     # ⬜
│   │   └── model/
│   │       ├── DeploySpec.kt           # ⬜
│   │       └── DeployState.kt          # ⬜
│   │
│   ├── pkg/
│   │   ├── rawdata/
│   │   │   ├── domain/
│   │   │   │   ├── RawDataRecord.kt   # ✅
│   │   │   │   └── OutboxEntry.kt     # ⬜ NEW
│   │   │   ├── ports/
│   │   │   │   ├── RawDataRepositoryPort.kt  # ✅
│   │   │   │   └── OutboxRepositoryPort.kt   # ⬜ NEW
│   │   │   └── adapters/
│   │   │       ├── InMemoryRawDataRepository.kt    # ✅
│   │   │       ├── InMemoryOutboxRepository.kt     # ⬜ NEW
│   │   │       ├── JooqRawDataRepository.kt        # ⬜ Phase B
│   │   │       └── JooqOutboxRepository.kt         # ⬜ Phase B
│   │   │
│   │   ├── slices/
│   │   │   ├── domain/
│   │   │   │   ├── SliceRecord.kt     # ✅
│   │   │   │   └── InvertedIndexEntry.kt # ✅
│   │   │   ├── ports/
│   │   │   │   ├── SliceRepositoryPort.kt  # ✅
│   │   │   │   └── InvertedIndexRepositoryPort.kt # ✅
│   │   │   └── adapters/
│   │   │       ├── InMemorySliceRepository.kt  # ✅
│   │   │       └── JooqSliceRepository.kt      # ⬜ Phase B
│   │   │
│   │   ├── changeset/
│   │   │   ├── domain/
│   │   │   │   ├── ChangeSet.kt       # ✅
│   │   │   │   └── ImpactMap.kt       # ⬜ NEW (v1.1)
│   │   │   └── ports/
│   │   │       └── ChangeSetRepositoryPort.kt # ⬜ NEW (v1.1)
│   │   │
│   │   ├── contracts/
│   │   │   ├── domain/                # ✅
│   │   │   ├── ports/                 # ✅
│   │   │   └── adapters/
│   │   │       ├── LocalYamlContractRegistryAdapter.kt  # ✅
│   │   │       └── DynamoDBContractRegistryAdapter.kt   # ⬜ Phase B
│   │   │
│   │   └── orchestration/
│   │       └── application/
│   │           ├── IngestWorkflow.kt    # ✅
│   │           ├── SlicingWorkflow.kt   # ✅
│   │           └── QueryViewWorkflow.kt # ✅
│   │
│   └── apps/
│       ├── runtimeapi/
│       │   ├── dto/
│       │   │   ├── Requests.kt        # ⬜ NEW
│       │   │   └── Responses.kt       # ⬜ NEW
│       │   ├── routes/                # ✅
│       │   ├── wiring/                # ✅
│       │   └── Application.kt         # ✅
│       │
│       └── worker/
│           └── OutboxPollingWorker.kt # ⬜ Phase B
```

---

## 🔄 작업 순서

```
Phase A (인터페이스) ✅
├── A-1: Enum 정의 ✅
├── A-2: OutboxEntry 도메인 ✅
├── A-3: DomainError 확장 ✅
├── A-4: Port Interfaces ✅
├── A-5: API DTO 정리 ✅
└── A-6: 기존 도메인에 Enum 적용 ✅
          │
          ▼
Phase B (구현) ✅ ALL COMPLETE
├── B-0: Workflow 통합 ✅
├── B-1: InMemory Adapters ✅
├── B-2: Polling Worker ✅
├── B-3: jOOQ Adapters ✅
├── B-4: Testcontainers ✅
└── B-5: DynamoDB Adapter ✅
          │
          ▼
Phase C (RFC-IMPL 마무리) ⏳
├── C-1: DynamoDB 캐싱 ⏳
├── C-2: DynamoDB checksum ⏳
└── C-3: Readiness 동적 wiring ⏳
          │
          ▼
Phase D (Core Business Logic) ✅
├── D-1: SliceRecord.tombstone (P0) ✅
├── D-2: RuleSet 도메인 + 로딩 (P0) ✅
├── D-3: SlicingEngine (P0) ✅
├── D-4: JoinSpec 실행 (P0) ✅
├── D-5: ViewDefinition + Policy (P1) ⏳
├── D-6: ContractStatusGate (P1) ⏳
├── D-7: ImpactMap 계산 (P1) ✅
├── D-8: INCREMENTAL Slicing (P1) ✅
└── D-9: Inverted Index 빌더 (P1) ✅
          │
          ▼
Phase E (Fluent SDK DX) ⬜ RFC-IMPL-011
├── E-1: Core SDK (IvmClient, IngestContext, ProductDsl) ⬜
├── E-2: Deploy DSL (compile/ship/cutover) ⬜
├── E-3: Sink DSL (opensearch/personalize) ⬜
├── E-4: Shortcut APIs (deployNow/deployQueued) ⬜
├── E-5: Async & Status (DeployJob, StateMachine) ⬜
├── E-6: Compiler Targets (RFC-009) ⬜
└── E-7: Contract Codegen ⬜
```

### 의존성 체인
```
A-6 (SliceType 적용)
  │
  ▼
B-0 (Workflow 통합) ← RawData + Outbox 같은 트랜잭션
  │
  ▼
B-2 (Polling Worker) ← Outbox에서 PENDING 읽어서 처리
  │
  ▼
B-3 (jOOQ) ← 실제 DB 트랜잭션 필요
```

---

## 🚀 Quick Commands

```bash
# 전체 체크 (InMemory 모드)
./gradlew checkAll -x flywayMigrate -x jooqCodegen

# 앱 실행 (InMemory 모드)
./gradlew run -x flywayMigrate -x jooqCodegen

# PostgreSQL + jOOQ (Phase B)
docker-compose up -d postgres
./gradlew flywayMigrate jooqCodegen
./gradlew checkAll
```

