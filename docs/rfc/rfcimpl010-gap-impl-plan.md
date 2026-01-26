## RFC-IMPL-010 GAP Implementation Plan (SOTA)

Status: Draft (Gap Closure Plan)
Created: 2026-01-25
Scope: 현재 코드 기준 “구현은 됐는데 실제로 안 붙어있는/스펙 불일치” 갭을 닫아 RFC-IMPL-010을 **실제 동작** 상태로 완성

---

## 0. Executive Summary

현재 repo는 Phase C 대부분(캐시/체크섬/레디니스)과 Phase D 일부(엔진/증분/인덱스 빌더/게이트)가 **코드 레벨로는 존재**하지만,
다음 이유로 RFC-IMPL-010의 “핵심 비즈니스 로직이 실제로 작동한다” 상태는 아직 아니다:

1. **D-4 JoinExecutor는 DI wiring 누락**으로 런타임에서 항상 비활성화
2. **RuleSet(로더/YAML) ↔ SlicingEngine 기대(joinSpec/indexSpec) 형태 불일치**로 JOIN/INDEX가 “정의돼도” 실행/생성되기 어려움
3. **D-5 ViewDefinition + Policy가 QueryViewWorkflow에 미연동**(TODO 남음, 정책 미적용)
4. (운영 관점) **InvertedIndexRepositoryPort의 영속 어댑터 부재**로 production 모듈에서 인덱스가 메모리로만 저장됨
5. (트리거 관점) **OutboxPollingWorker가 FULL 슬라이싱만 호출**하여 INCREMENTAL 경로가 런타임에서 사용되지 않음
6. (Readiness 관점) **InvertedIndexRepositoryPort가 HealthCheckable로 수집되지 않음**(ready 체크 대상 누락 가능)

이 문서는 위 갭을 닫기 위한 **최소 변경 우선**의 수정보완 계획(SOTA)을 제공한다.

---

## 1. Verified Current State (Evidence)

### 1-1. JoinExecutor: 구현 ✅ / 연동 ❌
- 구현: `src/main/kotlin/.../pkg/slices/domain/JoinExecutor.kt`
- SlicingEngine은 `joinExecutor: JoinExecutor? = null`(기본값 null)로 설계됨: `src/main/kotlin/.../pkg/slices/domain/SlicingEngine.kt`
- DI에서 JoinExecutor를 만들거나 SlicingEngine에 주입하지 않음:
  - `src/main/kotlin/.../apps/runtimeapi/wiring/WorkflowModule.kt`에서 `SlicingEngine(contractRegistry = get())`만 생성

### 1-2. RuleSet: “joins/indexes” 지원 불일치
- RuleSet 도메인에는 `indexes: List<IndexSpec>`가 존재: `src/main/kotlin/.../pkg/contracts/domain/RuleSetContract.kt`
- 하지만 LocalYaml RuleSet 파서는:
  - `joins`는 top-level `contracts.domain.JoinSpec`로만 파싱하고
  - `slices[].joins`(slice-level join spec)는 파싱하지 않음
  - `indexes`는 파싱하지 않음  
  - 근거: `src/main/kotlin/.../pkg/contracts/adapters/LocalYamlContractRegistryAdapter.kt::parseRuleSet`
- DynamoDB RuleSet 파서도 `indexes` 파싱이 없음(현재 `RuleSetContract.indexes`는 default empty로 유지됨)
  - 근거: `src/main/kotlin/.../pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt::parseRuleSet`

### 1-3. RuleSet YAML 자체가 join/index를 사용하지 않음
- `src/main/resources/contracts/v1/ruleset.v1.yaml`에서 `joins: []`, `indexes:` 없음
  - 즉, D-4/D-9의 “동작 검증”이 기본 설정만으로는 불가

### 1-4. QueryViewWorkflow: ViewDefinition 미사용 (TODO)
- `src/main/kotlin/.../pkg/orchestration/application/QueryViewWorkflow.kt`는
  - sliceTypes를 요청 파라미터로 받고, 누락 시 항상 fail-closed
  - `ViewDefinitionContract` 로딩/정책 적용이 TODO로 남음

### 1-5. 운영 모듈에서 InvertedIndex 저장이 InMemory
- `productionAdapterModule` 및 `jooqAdapterModule`에서 `InMemoryInvertedIndexRepository()`를 사용
  - 근거: `src/main/kotlin/.../apps/runtimeapi/wiring/AdapterModule.kt`
- jOOQ 기반 `JooqInvertedIndexRepository`가 현재 없음(= 운영에서 인덱스 영속 불가)

### 1-6. Outbox 트리거는 FULL 슬라이싱만 호출
- `OutboxPollingWorker`는 `RAW_DATA_INGESTED` 이벤트 처리 시 `slicingWorkflow.execute(...)`만 호출함
  - 즉 `executeIncremental(...)`은 현재 런타임 워커에서 호출되지 않음

### 1-7. Readiness 체크에서 InvertedIndex 어댑터가 누락될 수 있음
- `/ready`는 `getAll<HealthCheckable>()`로 수집된 어댑터만 체크함
- 현재 `InvertedIndexRepositoryPort`는 module wiring에서 `HealthCheckable`로 bind되지 않음(= readiness에서 빠질 가능성)

---

## 2. Gap List (What’s Actually Missing)

### GAP-A (P0): JoinExecutor가 런타임에서 항상 비활성
- 원인: DI wiring 누락
- 영향: RuleSet에서 join이 정의돼도 slice payload enrichment가 절대 실행되지 않음

### GAP-B (P0): RuleSet 계약 표현과 실행 엔진이 서로 다른 “JoinSpec”를 기대
- 현재 모델이 2개 존재:
  - `pkg/contracts/domain/RuleSetContract.kt` 내부의 `contracts.domain.JoinSpec`(sourceSlice/targetEntity/joinPath/cardinality)
  - `pkg/slices/domain/JoinSpec.kt`(name/type/sourceFieldPath/targetKeyPattern/required)
- SlicingEngine은 `SliceDefinition.joins: List<pkg.slices.domain.JoinSpec>`만 실행함
- 현재 로더는 `SliceDefinition.joins`를 채우지 않음 → join 실행 트리거가 없음

### GAP-C (P1): RuleSet.indexes가 로드되지 않아 인덱스 빌더가 실질적으로 no-op
- 도메인/빌더는 존재하지만, 계약 로드 계층에서 indexes를 채우지 않음

### GAP-D (P1): QueryView가 ViewDefinition + Missing/Partial/Fallback 정책을 적용하지 않음
- TODO 남음 + DI 미주입
- API도 아직 “viewId + sliceTypes” 형태로 v1에 머무름

### GAP-E (P1~P2): 운영에서 Inverted Index 영속 저장이 불가
- Port는 사용되지만 production wiring이 InMemory
- 결국 fan-out/조회 가드가 운영에서 의미를 잃음

### GAP-F (P1): INCREMENTAL이 “구현은 됐지만” 런타임에서 트리거되지 않음
- Outbox 워커가 FULL만 호출
- 결과: 운영에서 항상 FULL 슬라이싱(비용/지연 증가), ChangeSet 기반 최적화가 사실상 미사용

### GAP-G (P2): Readiness 체크 대상이 실제 의존성과 불일치할 수 있음
- InvertedIndexRepo / ChangeSetRepo 등은 HealthCheckable로 수집되지 않을 수 있음
- 결과: `/ready`가 UP이어도 일부 핵심 의존성은 DOWN인 상태가 가능

---

## 3. Implementation Plan (SOTA, Minimal-Change-First)

### 3-1. Phase 0 (P0) — Wiring & Contract/Runtime Shape Alignment

#### P0-1: JoinExecutor DI wiring 추가
- **변경**: `apps/runtimeapi/wiring/WorkflowModule.kt`
  - `JoinExecutor` bean 생성
  - `SlicingEngine(joinExecutor = get())`로 주입
- **AC**
  - [ ] Koin 그래프에서 `JoinExecutor`가 생성됨
  - [ ] `SlicingEngine`에서 joinExecutor가 null이 아님(통합 테스트로 검증)

#### P0-2: “RuleSet joins” 단일 표현으로 정리 (택1)

**Option A (권장, 최소 파괴적): SliceDefinition에 join spec을 직접 넣는 방향으로 계약/로더를 맞춘다**
- RuleSet YAML에 `slices[].joins`를 정의 (JoinExecutor가 이해하는 형태)
- LocalYaml/DynamoDB RuleSet 파서가 `slices[].joins` → `pkg.slices.domain.JoinSpec`로 매핑
- `RuleSetContract.joins`(top-level)는 deprecated 처리하거나 제거(후속 리팩터)

**Option B: SlicingEngine이 top-level joins를 실행하도록 바꾸고 slice-level joins는 제거**
- SlicingEngine에서 `ruleSet.joins`를 sliceType별로 필터링 실행
- JoinExecutor가 이해하는 형태로 `contracts.domain.JoinSpec`를 확장(= 파급 큼)

**이 계획서는 Option A를 기준으로 한다.**

##### P0-2a: RuleSetContract 모델 정리
- **변경**
  - `pkg/contracts/domain/RuleSetContract.kt`:
    - `joins: List<JoinSpec>` 제거 또는 `@Deprecated` + TODO(차후 제거)
    - `SliceDefinition.joins`는 이미 존재하므로 유지
- **AC**
  - [ ] JoinSpec가 “하나의 의미”로만 사용됨(혼동 제거)

##### P0-2b: LocalYaml RuleSet 파서에 `slices[].joins` 파싱 추가
- **변경**: `pkg/contracts/adapters/LocalYamlContractRegistryAdapter.kt::parseRuleSet`
- **AC**
  - [ ] YAML에 `slices[].joins`가 있으면 `SliceDefinition.joins`로 채워짐
  - [ ] 잘못된 join spec 입력 시 fail-closed(ContractError)

##### P0-2c: DynamoDB RuleSet 파서에도 동일하게 적용
- **변경**: `pkg/contracts/adapters/DynamoDBContractRegistryAdapter.kt::parseRuleSet`
- **AC**
  - [ ] 동일한 JSON 구조를 로드해도 LocalYaml과 결과가 동치

---

### 3-2. Phase 1 (P1) — IndexSpec 로딩 + (선택) 예시 계약 추가

#### P1-1: RuleSet.indexes 파싱/로드 지원
- **변경**
  - LocalYaml: `ruleset.v1.yaml`의 `indexes:` 섹션을 파싱하여 `RuleSetContract.indexes` 채움
  - DynamoDB: `data.indexes`를 파싱하여 채움
- **AC**
  - [ ] indexSpec이 존재하면 `InvertedIndexBuilder` 결과가 0이 아니게 됨(테스트)
  - [ ] selector 문법 오류 시 fail-closed

#### P1-2: ruleset.v1.yaml에 최소 index 예시 추가(권장)
- **예**
  - `type: "brand"`
  - `selector: "$.brandId"` 또는 실제 payload 필드에 맞춘 selector
- **AC**
  - [ ] `/api/v1/slice` 실행 시 indexes가 저장 호출되는 것을 통합 테스트로 검증

---

### 3-3. Phase 2 (P1) — QueryView v2: ViewDefinition 기반 실행

#### P1-3: QueryViewWorkflow에 ViewDefinitionContract 연동
- **변경**
  - `QueryViewWorkflow`에 `ContractRegistryPort` 주입
  - `execute()` 시 `loadViewDefinitionContract`로 정책 로드
  - required/optional slices를 contract로부터 결정
  - MissingPolicy/PartialPolicy/FallbackPolicy 적용
- **API 방향 (택1)**
  - (A) 기존 API 유지: request의 `viewId`를 “contract id”로 간주하고 version은 config 기본값 사용
  - (B) API 변경: request에 `viewRef {id, version}` 도입 (RFC-IMPL-010 원안에 가까움)

**이 계획서는 A(기존 API 유지 + config로 version 주입)를 우선 권장**한다. 변경 범위가 가장 작고 마이그레이션이 쉽다.

- **AC**
  - [ ] FAIL_CLOSED: required 누락 시 즉시 에러
  - [ ] PARTIAL_ALLOWED + partialPolicy.allowed=true: required 누락 시에도 정책대로 동작
  - [ ] responseMeta.includeMissingSlices / includeUsedContracts 동작 테스트

---

### 3-4. Phase 3 (P1~P2) — Inverted Index 영속 어댑터(운영)

#### P1-4: jOOQ 기반 InvertedIndexRepositoryPort 구현
- **변경**
  - `pkg/slices/adapters/JooqInvertedIndexRepository.kt` 신규
  - migration/DDL 필요 시 Flyway 추가
  - `productionAdapterModule`에서 InMemory → Jooq로 교체
- **AC**
  - [ ] restart 후에도 index 조회가 유지됨(영속)
  - [ ] `putAllIdempotent` 멱등성 테스트

---

### 3-5. Phase 4 (P1) — INCREMENTAL Runtime Trigger (Outbox)

#### P1-5: OutboxPollingWorker에서 INCREMENTAL 선택 로직 추가
- **목표**: “구현된 INCREMENTAL”이 실제 운영에서 호출되도록 한다.
- **변경 방향(최소 변경)**:
  - (A) `RAW_DATA_INGESTED` 처리 시 “이전 버전 존재 여부”를 조회해 있으면 `executeIncremental(from,to,ruleSetRef)` 호출
  - (B) 이벤트 페이로드에 `fromVersion`을 포함하도록 스키마 확장(더 명확하지만 이벤트 변경 필요)
- **AC**
  - [ ] fromVersion 미존재(첫 버전) → FULL
  - [ ] fromVersion 존재 → INCREMENTAL
  - [ ] FULL == INCREMENTAL 불변식 테스트는 기존 유지(추가: 워커 경로 통합 테스트)

---

### 3-6. Phase 5 (P2) — Readiness Coverage Hardening

#### P2-1: 핵심 어댑터를 HealthCheckable로 일관되게 수집
- **변경**
  - InvertedIndexRepo(및 운영에서 critical로 보는 repo들)를 `HealthCheckable`로 bind
  - InMemory 구현은 단순 `true`, jOOQ/DynamoDB는 실제 연결/쿼리 기반 체크
- **AC**
  - [ ] `/ready`가 실제 운영 의존성과 “대부분 일치”하도록 보장

---

## 4. Test Plan (Non-Negotiable)

### 4-1. Join path
- [ ] “RuleSet에 join spec이 있을 때” 실제로 JOIN이 실행되어 slice payload가 enrichment 되는 통합 테스트
- [ ] required=true/false, target missing 케이스
- [ ] 결정성: 동일 입력 → 동일 결과(해시 포함)

### 4-2. ViewDefinition path
- [ ] ViewDefinitionContract 로딩 실패 → fail-closed
- [ ] MissingPolicy/PartialPolicy 조합별 행위 테스트

### 4-3. Index path
- [ ] indexSpec 존재 시 InvertedIndexEntry 생성됨
- [ ] selector가 배열/중첩/누락/null 일 때 동작

---

## 5. Rollout / Migration Notes

- **Backward compatibility 우선**: Query API는 당장 유지(A안), 서버 config에 view contract version만 추가
- **계약 진화**: RuleSet join/index 섹션은 v1 YAML에 optional로 추가(미기재 시 기존 동작 유지)
- **Fail-closed 유지**: 계약 파싱/정책 적용은 기본적으로 fail-closed로 구현

---

## 6. Deliverables Checklist

- [x] WorkflowModule: JoinExecutor wiring + SlicingEngine 주입 (GAP-A ✅ 2026-01-25)
- [x] RuleSetContract: JoinSpec 중복/혼동 제거 - slices[].joins로 단일화 (GAP-B ✅)
- [x] LocalYaml/DynamoDB: RuleSet `slices[].joins` + `indexes` 파싱 (GAP-B, GAP-C ✅)
- [x] ruleset.v1.yaml: indexes 예시 추가됨 (기존 완료)
- [x] QueryViewWorkflow: ViewDefinitionContract 기반 실행 + 정책 적용 (GAP-D ✅)
- [x] (운영) JooqInvertedIndexRepository + module wiring (GAP-E ✅)
- [x] OutboxPollingWorker: INCREMENTAL 트리거 경로 연결 - executeAuto() (GAP-F ✅)
- [x] Readiness: HealthCheckable coverage 보강 (GAP-G ✅)

---

## 7. Implementation Summary (2026-01-25)

### GAP-A (P0): JoinExecutor DI wiring
- `WorkflowModule.kt`: JoinExecutor 생성 및 SlicingEngine에 주입

### GAP-B (P0): RuleSet joins 단일 표현 + 로더 수정
- `LocalYamlContractRegistryAdapter.kt`: slices[].joins 파싱 추가
- `DynamoDBContractRegistryAdapter.kt`: slices[].joins 파싱 추가

### GAP-C (P1): RuleSet.indexes 파싱
- 이미 구현되어 있음 (LocalYaml, DynamoDB 모두)

### GAP-D (P1): QueryViewWorkflow ViewDefinition 연동
- `QueryViewWorkflow.kt`: ViewDefinitionContract 기반 execute() 구현
- MissingPolicy/PartialPolicy/FallbackPolicy 정책 적용
- v2 API 엔드포인트 추가 (`/api/v2/query`)
- DomainError.MissingSliceError 추가

### GAP-E (P1~P2): JooqInvertedIndexRepository 구현
- `JooqInvertedIndexRepository.kt`: 신규 구현
- `V009__inverted_index_v2.sql`: 스키마 확장 마이그레이션
- jooqAdapterModule, productionAdapterModule에 바인딩

### GAP-F (P1): OutboxWorker INCREMENTAL 트리거
- `SlicingWorkflow.kt`: executeAuto() 메서드 추가 (자동 FULL/INCREMENTAL 선택)
- `OutboxPollingWorker.kt`: execute() → executeAuto() 호출로 변경

### GAP-G (P2): Readiness HealthCheckable 커버리지
- `InMemoryInvertedIndexRepository.kt`: HealthCheckable 구현 추가
- `InMemoryChangeSetRepository.kt`: HealthCheckable 구현 추가
- `AdapterModule.kt`: 모든 레포지토리에 HealthCheckable 바인딩 추가

