# RFC-IMPL-011 Implementation Plan — Fluent SDK DX

Status: **Ready for Implementation**
Created: 2026-01-25
Target: L12급 빡센 구현

---

## 📋 Implementation Phases Overview

```
Phase 1: Core SDK Infrastructure ──────────────────────────────▶ 필수 (P0)
Phase 2: Deploy Orchestration DSL ─────────────────────────────▶ 필수 (P0)
Phase 3: Sink DSL ─────────────────────────────────────────────▶ 필수 (P0)
Phase 4: Shortcut APIs ────────────────────────────────────────▶ 권장 (P1)
Phase 5: Async & Status ───────────────────────────────────────▶ 권장 (P1)
Phase 6: Compiler Targets (RFC-009) ───────────────────────────▶ 권장 (P1)
Phase 7: Contract Codegen ─────────────────────────────────────▶ 확장 (P2)
```

---

## 🎯 Phase 1: Core SDK Infrastructure (P0, 필수)

### 목표
`Ivm.client().ingest().product { ... }` 기본 체이닝 동작

### 1-1. DslMarker 정의
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/dsl/markers/IvmDslMarker.kt` | `@DslMarker` 어노테이션 정의 |

```kotlin
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class IvmDslMarker
```

### 1-2. IvmClient 진입점
| # | 파일 | 설명 |
|---|------|------|
| 2 | `sdk/client/IvmClientConfig.kt` | 클라이언트 설정 |
| 3 | `sdk/client/IvmClient.kt` | `Ivm.client()` 싱글톤 + 팩토리 |

### 1-3. Ingest DSL
| # | 파일 | 설명 |
|---|------|------|
| 4 | `sdk/dsl/ingest/IngestContext.kt` | `.ingest()` 컨텍스트 |

### 1-4. Entity DSL (Product 먼저)
| # | 파일 | 설명 |
|---|------|------|
| 5 | `sdk/dsl/entity/EntityInput.kt` | 엔티티 공통 인터페이스 |
| 6 | `sdk/dsl/entity/ProductDsl.kt` | `.product { ... }` Builder |
| 7 | `sdk/dsl/entity/ProductInput.kt` | Product 입력 데이터 클래스 |

### 1-5. DeployableContext 기본
| # | 파일 | 설명 |
|---|------|------|
| 8 | `sdk/dsl/deploy/DeployableContext.kt` | Entity → Deploy 체이닝 컨텍스트 |

### 1-6. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 9 | `test/.../sdk/ProductBuilderTest.kt` | Builder 단위 테스트 |
| 10 | `test/.../sdk/DslMarkerScopeTest.kt` | 스코프 격리 테스트 |

### Phase 1 Acceptance Criteria
- [ ] `Ivm.client()` 호출 가능
- [ ] `.ingest()` 체이닝 가능
- [ ] `.product { sku("X"); name("Y"); price(100) }` 빌더 동작
- [ ] 필수 필드 누락 시 예외 발생
- [ ] `@IvmDslMarker` 스코프 격리 검증

---

## 🎯 Phase 2: Deploy Orchestration DSL (P0, 필수)

### 목표
```kotlin
.deploy {
    compile.sync()
    ship.async { opensearch() }
    cutover.ready()
}
```

### 2-1. Deploy 모델
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/model/CompileMode.kt` | Sync, Async, SyncWithTargets |
| 2 | `sdk/model/ShipMode.kt` | Sync, Async |
| 3 | `sdk/model/CutoverMode.kt` | Ready, Done |
| 4 | `sdk/model/DeploySpec.kt` | 전체 Deploy 스펙 |
| 5 | `sdk/model/ShipSpec.kt` | Ship 스펙 (mode + sinks) |

### 2-2. Deploy Builder
| # | 파일 | 설명 |
|---|------|------|
| 6 | `sdk/dsl/deploy/DeployBuilder.kt` | `.deploy { ... }` 빌더 |
| 7 | `sdk/dsl/deploy/DeployAsyncBuilder.kt` | `.deployAsync { ... }` 빌더 |
| 8 | `sdk/dsl/deploy/CompileAccessor.kt` | `compile.sync()` / `compile.async()` |
| 9 | `sdk/dsl/deploy/ShipAccessor.kt` | `ship.sync {}` / `ship.async {}` |
| 10 | `sdk/dsl/deploy/CutoverAccessor.kt` | `cutover.ready()` / `cutover.done()` |

### 2-3. Axis Validation
| # | 파일 | 설명 |
|---|------|------|
| 11 | `sdk/validation/AxisValidator.kt` | 축 조합 검증 로직 |

**핵심 검증 규칙 (RFC-008: 3)**:
```
| Compile | Ship   | 허용 |
|---------|--------|------|
| sync    | sync   | ⭕   |
| sync    | async  | ⭕   |
| async   | async  | ⭕   |
| async   | sync   | ❌   | ← 타입 레벨 + 런타임 차단
```

### 2-4. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 12 | `test/.../sdk/DeployBuilderTest.kt` | Deploy 빌더 테스트 |
| 13 | `test/.../sdk/AxisValidationTest.kt` | 축 조합 검증 테스트 |

### Phase 2 Acceptance Criteria
- [ ] `compile.sync()` / `compile.async()` 동작
- [ ] `ship.sync {}` / `ship.async {}` 동작
- [ ] `cutover.ready()` / `cutover.done()` 동작
- [ ] `compile.async + ship.sync` 조합 시 예외 발생
- [ ] `DeployAsyncBuilder`에서 `ship.sync` 메서드 미존재 (타입 레벨 차단)

---

## 🎯 Phase 3: Sink DSL (P0, 필수)

### 목표
```kotlin
ship.async {
    opensearch { index("products"); batchSize(1000) }
    personalize { datasetArn("arn:...") }
}
```

### 3-1. Sink 모델
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/model/SinkSpec.kt` | Sink 스펙 sealed interface |
| 2 | `sdk/model/OpenSearchSinkSpec.kt` | OpenSearch 스펙 |
| 3 | `sdk/model/PersonalizeSinkSpec.kt` | Personalize 스펙 |

### 3-2. Sink Builder
| # | 파일 | 설명 |
|---|------|------|
| 4 | `sdk/dsl/sink/SinkBuilder.kt` | Sink 컨테이너 빌더 |
| 5 | `sdk/dsl/sink/OpenSearchBuilder.kt` | `opensearch { ... }` 빌더 |
| 6 | `sdk/dsl/sink/PersonalizeBuilder.kt` | `personalize { ... }` 빌더 |

### 3-3. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 7 | `test/.../sdk/SinkBuilderTest.kt` | Sink 빌더 테스트 |

### Phase 3 Acceptance Criteria
- [ ] `opensearch()` 기본 호출 가능
- [ ] `opensearch { index("x"); batchSize(500) }` 설정 가능
- [ ] `personalize()` 기본 호출 가능
- [ ] 여러 Sink 동시 등록 가능

---

## 🎯 Phase 4: Shortcut APIs (P1, 권장)

### 목표
```kotlin
.deployNow { opensearch() }
.deployNowAndShipNow { opensearch() }
.deployQueued { opensearch() }
```

### 4-1. Shortcut 메서드
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/dsl/shortcuts/DeployShortcuts.kt` | 확장 함수로 Shortcut 제공 |

**Shortcut 정의 (RFC-008: 11)**:
```kotlin
fun deployNow { ... }       = compile.sync + ship.async + cutover.ready
fun deployNowAndShipNow { } = compile.sync + ship.sync + cutover.ready
fun deployQueued { ... }    = compile.async + ship.async + cutover.ready
```

### 4-2. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 2 | `test/.../sdk/ShortcutApiTest.kt` | Shortcut 동작 검증 |

### Phase 4 Acceptance Criteria
- [ ] `deployNow {}` = compile.sync + ship.async 검증
- [ ] `deployNowAndShipNow {}` = compile.sync + ship.sync 검증
- [ ] `deployQueued {}` = compile.async + ship.async 검증

---

## 🎯 Phase 5: Async & Status (P1, 권장)

### 목표
```kotlin
val job = Ivm.client().ingest().product { ... }.deployQueued { ... }
val status = Ivm.client().deploy.status(job.jobId)
val result = Ivm.client().deploy.await(job.jobId)
```

### 5-1. 모델
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/model/DeployState.kt` | QUEUED, RUNNING, READY, SINKING, DONE, FAILED |
| 2 | `sdk/model/DeployJob.kt` | 비동기 Job 모델 |
| 3 | `sdk/model/DeployJobStatus.kt` | Job 상태 응답 |
| 4 | `sdk/model/DeployResult.kt` | 동기 실행 결과 |

### 5-2. 상태 머신
| # | 파일 | 설명 |
|---|------|------|
| 5 | `sdk/model/DeployEvent.kt` | 상태 전이 이벤트 |
| 6 | `sdk/execution/StateMachine.kt` | 상태 전이 로직 |

### 5-3. Status API
| # | 파일 | 설명 |
|---|------|------|
| 7 | `sdk/client/DeployStatusApi.kt` | `deploy.status()` / `deploy.await()` |

### 5-4. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 8 | `test/.../sdk/StateMachineTest.kt` | 상태 전이 테스트 |
| 9 | `test/.../sdk/DeployStatusApiTest.kt` | Status API 테스트 |

### Phase 5 Acceptance Criteria
- [ ] `DeployJob` 반환 및 `jobId` 획득
- [ ] `deploy.status(jobId)` 상태 조회
- [ ] `deploy.await(jobId)` 완료 대기
- [ ] 상태 머신 전이 규칙 검증 (QUEUED → RUNNING → READY → SINKING → DONE)
- [ ] 잘못된 상태 전이 시 에러

---

## 🎯 Phase 6: Compiler Targets (P1, 권장)

### 목표 (RFC-009)
```kotlin
.deploy {
    compile {
        targets {
            searchDoc()
            recoFeed()
        }
    }
    ship.async { opensearch() }
}
```

### 6-1. Targets DSL
| # | 파일 | 설명 |
|---|------|------|
| 1 | `sdk/model/TargetRef.kt` | 타겟 참조 모델 |
| 2 | `sdk/dsl/deploy/CompileTargetsBuilder.kt` | `compile { targets { } }` 빌더 |
| 3 | `sdk/dsl/deploy/TargetsBuilder.kt` | `searchDoc()`, `recoFeed()` |

### 6-2. Plan 설명 API
| # | 파일 | 설명 |
|---|------|------|
| 4 | `sdk/model/DeployPlan.kt` | Plan 설명 결과 |
| 5 | `sdk/model/DependencyGraph.kt` | 의존성 그래프 |
| 6 | `sdk/model/ExecutionStep.kt` | 실행 단계 |
| 7 | `sdk/client/PlanExplainApi.kt` | `explainLastPlan()` |

### 6-3. 테스트
| # | 파일 | 설명 |
|---|------|------|
| 8 | `test/.../sdk/TargetsDslTest.kt` | Targets DSL 테스트 |
| 9 | `test/.../sdk/PlanExplainTest.kt` | Plan 설명 테스트 |

### Phase 6 Acceptance Criteria
- [ ] `compile { targets { searchDoc() } }` 동작
- [ ] 여러 타겟 동시 지정 가능
- [ ] `explainLastPlan()` Plan 정보 반환

---

## 🎯 Phase 7: Contract Codegen (P2, 확장)

### 목표
RuleSet/SinkRule 계약에서 EntityDsl/SinkDsl 자동 생성

### 7-1. Codegen 엔진
| # | 파일 | 설명 |
|---|------|------|
| 1 | `codegen/EntityDslGenerator.kt` | RuleSet → EntityDsl |
| 2 | `codegen/SinkDslGenerator.kt` | SinkRule → SinkDsl |
| 3 | `codegen/DslCodegenConfig.kt` | 코드젠 설정 |

### 7-2. Gradle 플러그인
| # | 파일 | 설명 |
|---|------|------|
| 4 | `buildSrc/.../IvmCodegenPlugin.kt` | Gradle 플러그인 |
| 5 | `build.gradle.kts` | 플러그인 적용 |

### Phase 7 Acceptance Criteria
- [ ] `./gradlew generateIvmDsl` 실행 시 DSL 코드 생성
- [ ] 생성된 코드가 수동 작성과 동일 인터페이스
- [ ] Contract 변경 시 재생성

---

## 📁 최종 Directory Structure

```
src/main/kotlin/com/oliveyoung/ivmlite/
  sdk/
    client/
      Ivm.kt                        # object Ivm { fun client() }
      IvmClient.kt                  # 클라이언트 클래스
      IvmClientConfig.kt            # 설정
      DeployStatusApi.kt            # Phase 5
      PlanExplainApi.kt             # Phase 6
      
    dsl/
      markers/
        IvmDslMarker.kt             # @DslMarker
        
      ingest/
        IngestContext.kt            # .ingest()
        
      entity/
        EntityInput.kt              # sealed interface
        ProductDsl.kt               # .product { }
        ProductInput.kt
        BrandDsl.kt                 # (추후)
        CategoryDsl.kt              # (추후)
        
      deploy/
        DeployableContext.kt        # 체이닝 컨텍스트
        DeployBuilder.kt            # .deploy { }
        DeployAsyncBuilder.kt       # .deployAsync { }
        CompileAccessor.kt          # compile.sync/async
        ShipAccessor.kt             # ship.sync/async
        CutoverAccessor.kt          # cutover.ready/done
        CompileTargetsBuilder.kt    # Phase 6
        TargetsBuilder.kt           # Phase 6
        
      sink/
        SinkBuilder.kt              # 컨테이너
        OpenSearchBuilder.kt        # opensearch { }
        PersonalizeBuilder.kt       # personalize { }
        
      shortcuts/
        DeployShortcuts.kt          # Phase 4
        
    model/
      CompileMode.kt
      ShipMode.kt
      CutoverMode.kt
      DeploySpec.kt
      ShipSpec.kt
      SinkSpec.kt
      OpenSearchSinkSpec.kt
      PersonalizeSinkSpec.kt
      TargetRef.kt                  # Phase 6
      DeployState.kt                # Phase 5
      DeployEvent.kt                # Phase 5
      DeployJob.kt                  # Phase 5
      DeployJobStatus.kt            # Phase 5
      DeployResult.kt
      DeployPlan.kt                 # Phase 6
      DependencyGraph.kt            # Phase 6
      ExecutionStep.kt              # Phase 6
      
    validation/
      AxisValidator.kt
      
    execution/
      DeployExecutor.kt
      StateMachine.kt               # Phase 5
      
  codegen/                          # Phase 7
    EntityDslGenerator.kt
    SinkDslGenerator.kt
    DslCodegenConfig.kt
```

---

## 🔄 구현 순서 (의존성 체인)

```
Phase 1: Core SDK
├── 1-1: DslMarker
├── 1-2: IvmClient
├── 1-3: IngestContext
├── 1-4: ProductDsl
└── 1-5: DeployableContext (기본)
          │
          ▼
Phase 2: Deploy DSL
├── 2-1: Deploy Models
├── 2-2: DeployBuilder
├── 2-3: Axis Validation
          │
          ▼
Phase 3: Sink DSL
├── 3-1: Sink Models
├── 3-2: Sink Builders
          │
          ▼
Phase 4: Shortcuts (독립)
          │
          ▼
Phase 5: Async & Status
├── 5-1: State Models
├── 5-2: StateMachine
├── 5-3: Status API
          │
          ▼
Phase 6: Compiler Targets
├── 6-1: Targets DSL
├── 6-2: Plan API
          │
          ▼
Phase 7: Codegen (독립)
```

---

## 🚀 Quick Start Commands

```bash
# Phase 1-3 구현 후 기본 테스트
./gradlew test --tests "*.sdk.*"

# 전체 체크
./gradlew checkAll

# 통합 테스트 (Phase 5+)
./gradlew integrationTest --tests "*FluentSdk*"
```

---

## ✅ Golden Test Cases (RFC 예시 전체)

```kotlin
// RFC-008 Section 9-1: Raw Input DSL
@Test fun `RFC-008 9-1 Raw Input DSL`() {
    Ivm.client()
        .ingest()
        .product {
            sku("ABC-123")
            name("Moisture Cream")
            price(19000)
            currency("KRW")
        }
}

// RFC-008 Section 10-1: Default Deploy
@Test fun `RFC-008 10-1 Default Deploy`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deploy {
            ship.async {
                opensearch()
                personalize()
            }
        }
}

// RFC-008 Section 10-2: All Sync
@Test fun `RFC-008 10-2 All Sync`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deploy {
            compile.sync()
            ship.sync {
                opensearch()
                personalize()
            }
        }
}

// RFC-008 Section 10-3: Async Deploy
@Test fun `RFC-008 10-3 Async Deploy`() {
    val job = Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deployAsync {
            compile.async()
            ship.async {
                opensearch()
                personalize()
            }
        }
    
    Ivm.client().deploy.status(job.jobId)
}

// RFC-008 Section 10-4: Done Cutover
@Test fun `RFC-008 10-4 Done Cutover`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deployAsync {
            compile.async()
            cutover.done()
            ship.async {
                opensearch()
                personalize()
            }
        }
}

// RFC-008 Section 11-1: deployNow
@Test fun `RFC-008 11-1 deployNow`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deployNow {
            opensearch()
            personalize()
        }
}

// RFC-008 Section 11-2: deployNowAndShipNow
@Test fun `RFC-008 11-2 deployNowAndShipNow`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deployNowAndShipNow {
            opensearch()
            personalize()
        }
}

// RFC-008 Section 11-3: deployQueued
@Test fun `RFC-008 11-3 deployQueued`() {
    val job = Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deployQueued {
            opensearch()
            personalize()
        }
}

// RFC-009 Section 11-1: Compile with Targets
@Test fun `RFC-009 11-1 Compile with Targets`() {
    Ivm.client()
        .ingest()
        .product { /* ... */ }
        .deploy {
            compile {
                targets {
                    searchDoc()
                    recoFeed()
                }
            }
            ship.async {
                opensearch()
                personalize()
            }
        }
}

// RFC-009 Section 11-2: Explain Plan
@Test fun `RFC-009 11-2 Explain Plan`() {
    val plan = Ivm.client().explainLastPlan(deployId)
    
    assertNotNull(plan.graph)
    assertNotNull(plan.activatedRules)
    assertNotNull(plan.executionSteps)
}
```

---

## 📊 진행 체크리스트

### Phase 1: Core SDK Infrastructure
- [ ] 1-1: `IvmDslMarker.kt`
- [ ] 1-2: `IvmClientConfig.kt`
- [ ] 1-3: `IvmClient.kt` + `Ivm.kt`
- [ ] 1-4: `IngestContext.kt`
- [ ] 1-5: `EntityInput.kt`
- [ ] 1-6: `ProductDsl.kt` + `ProductInput.kt`
- [ ] 1-7: `DeployableContext.kt` (기본)
- [ ] 1-8: `ProductBuilderTest.kt`
- [ ] 1-9: `DslMarkerScopeTest.kt`

### Phase 2: Deploy Orchestration DSL
- [ ] 2-1: `CompileMode.kt`
- [ ] 2-2: `ShipMode.kt`
- [ ] 2-3: `CutoverMode.kt`
- [ ] 2-4: `DeploySpec.kt` + `ShipSpec.kt`
- [ ] 2-5: `DeployBuilder.kt`
- [ ] 2-6: `DeployAsyncBuilder.kt`
- [ ] 2-7: `CompileAccessor.kt`
- [ ] 2-8: `ShipAccessor.kt`
- [ ] 2-9: `CutoverAccessor.kt`
- [ ] 2-10: `AxisValidator.kt`
- [ ] 2-11: `DeployBuilderTest.kt`
- [ ] 2-12: `AxisValidationTest.kt`

### Phase 3: Sink DSL
- [ ] 3-1: `SinkSpec.kt`
- [ ] 3-2: `OpenSearchSinkSpec.kt`
- [ ] 3-3: `PersonalizeSinkSpec.kt`
- [ ] 3-4: `SinkBuilder.kt`
- [ ] 3-5: `OpenSearchBuilder.kt`
- [ ] 3-6: `PersonalizeBuilder.kt`
- [ ] 3-7: `SinkBuilderTest.kt`

### Phase 4: Shortcut APIs
- [ ] 4-1: `DeployShortcuts.kt`
- [ ] 4-2: `ShortcutApiTest.kt`

### Phase 5: Async & Status
- [ ] 5-1: `DeployState.kt`
- [ ] 5-2: `DeployEvent.kt`
- [ ] 5-3: `DeployJob.kt`
- [ ] 5-4: `DeployJobStatus.kt`
- [ ] 5-5: `DeployResult.kt`
- [ ] 5-6: `StateMachine.kt`
- [ ] 5-7: `DeployStatusApi.kt`
- [ ] 5-8: `StateMachineTest.kt`
- [ ] 5-9: `DeployStatusApiTest.kt`

### Phase 6: Compiler Targets
- [ ] 6-1: `TargetRef.kt`
- [ ] 6-2: `CompileTargetsBuilder.kt`
- [ ] 6-3: `TargetsBuilder.kt`
- [ ] 6-4: `DeployPlan.kt`
- [ ] 6-5: `DependencyGraph.kt`
- [ ] 6-6: `ExecutionStep.kt`
- [ ] 6-7: `PlanExplainApi.kt`
- [ ] 6-8: `TargetsDslTest.kt`
- [ ] 6-9: `PlanExplainTest.kt`

### Phase 7: Contract Codegen
- [ ] 7-1: `EntityDslGenerator.kt`
- [ ] 7-2: `SinkDslGenerator.kt`
- [ ] 7-3: `DslCodegenConfig.kt`
- [ ] 7-4: Gradle Plugin

### Golden Tests
- [ ] RFC-008 9-1: Raw Input DSL
- [ ] RFC-008 10-1: Default Deploy
- [ ] RFC-008 10-2: All Sync
- [ ] RFC-008 10-3: Async Deploy
- [ ] RFC-008 10-4: Done Cutover
- [ ] RFC-008 11-1: deployNow
- [ ] RFC-008 11-2: deployNowAndShipNow
- [ ] RFC-008 11-3: deployQueued
- [ ] RFC-009 11-1: Compile with Targets
- [ ] RFC-009 11-2: Explain Plan

---

## 🎯 시작점 선택

1. **Phase 1부터 순차적**: 기반부터 탄탄하게 (권장)
2. **Phase 1 + 2 + 3 병렬**: Core 빠르게 완성
3. **MVP 먼저**: `deployNow {}` 최소 동작 우선
