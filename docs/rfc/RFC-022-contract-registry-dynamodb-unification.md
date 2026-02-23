# RFC-022: Contract Registry DynamoDB 통합

## 개요

디스크(YAML)에서 하드코딩으로 Contract/규칙을 로드하는 모든 경로를 정리하고, **DynamoDB Contract Registry**를 단일 소스로 사용하도록 통합한다.

## 배경

- **EntityContractResolver**: 이미 ContractRegistryPort 사용 (완료)
- **WorkflowGraphBuilder**: ContractFileRegistry + `/contracts/v1` 디스크 직접 로드
- **AdminContractService**: `/contracts/v1` 디스크 직접 스캔
- **ContractGraphService**: `loadDescriptorsInternal()` 디스크 직접 로드
- **LocalYamlSinkRuleRegistryAdapter**: `sinkrule-*.yaml` 디스크에서 SinkRule 로드
- **WorkerModule**: LocalYamlSinkRuleRegistryAdapter 사용 (resourcePath 하드코딩)

## 목표

1. Contract 조회 경로를 **ContractRegistryPort** 단일화
2. Production 환경에서 **DynamoDB**가 Contract SSOT
3. 개발/테스트는 LocalYaml 유지 (DI로 전환)

## 현재 디스크 하드코딩 사용처

| 위치 | 용도 | 전환 대상 |
|------|------|-----------|
| WorkflowGraphBuilder | ContractFileRegistry + getResourceAsStream | ContractRegistryPort |
| AdminContractService | loadAllContractsInternal() | ContractRegistryPort |
| ContractGraphService | loadDescriptorsInternal() | ContractRegistryPort |
| LocalYamlSinkRuleRegistryAdapter | sinkrule-*.yaml | SinkRuleRegistryPort (DynamoDB 어댑터) |
| WorkerModule | LocalYamlSinkRuleRegistryAdapter(resourcePath) | SinkRuleRegistryPort (DI) |

## DynamoDB Contract 범위

**현재 시드 대상** (SeedContractsToDynamoDB):
- RULESET
- VIEW_DEFINITION
- CHANGESET
- JOIN_SPEC

**미지원**:
- ENTITY_SCHEMA: DynamoDB에 없음. RuleSet.entityType으로 entityType 추출 가능.

**Phase 2 완료** (SINK_RULE):
- SINK_RULE: DynamoDBSinkRuleRegistryAdapter로 contract_registry 테이블에서 조회
- SeedContractsToDynamoDB에서 sinkrule-*.yaml 시드 지원

## 구현 계획

### Phase 1: ContractRegistryPort 기반 전환 (RULESET, VIEW_DEFINITION)

#### 1.1 WorkflowGraphBuilder
- **Before**: ContractFileRegistry + `getResourceAsStream("/contracts/v1/$fileName")`
- **After**: `ContractRegistryPort` 주입
  - `listContractRefs(RULESET)` + `loadRuleSetContract` → RuleSet 노드
  - `listViewDefinitions()` → ViewDefinition 노드
  - EntitySchema: RuleSet에서 entityType 추출 (ENTITY_SCHEMA YAML 대체)
  - SinkRule: `SinkRuleRegistryPort.findAllActive()` 사용 (Phase 1.4)
- **주의**: `build()`는 sync → `runBlocking`으로 suspend 호출

#### 1.2 AdminContractService
- **Before**: 디스크 스캔 (`/contracts/v1`)
- **After**: `ContractRegistryPort` 주입
  - `listContractRefs(kind)` + `load*Contract` → ContractInfo 변환
  - RULESET, VIEW_DEFINITION, CHANGESET, JOIN_SPEC만 지원
  - ENTITY_SCHEMA, SINK_RULE: ContractRegistryPort에 없으면 빈 목록 또는 fallback
- **AdminModule**: `AdminContractService(contractRegistry = get())`

#### 1.3 ContractGraphService
- **Before**: `loadDescriptorsInternal()` 디스크 로드
- **After**: `ContractRegistryPort` 주입
  - RuleSet/ViewDefinition → ContractDescriptor 변환
  - SinkRule: SinkRuleRegistryPort 사용

#### 1.4 WorkflowCanvasService / WorkflowGraphBuilder DI
- **AdminModule**: `WorkflowGraphBuilder(contractRegistry, sinkRuleRegistry)` 주입
- **WorkflowCanvasService**: 이미 WorkflowGraphBuilderPort 사용

### Phase 2: SinkRule DynamoDB 지원 ✅ 완료

- **DynamoDBSinkRuleRegistryAdapter**: contract_registry 테이블 GSI(kind-status-index)로 SINK_RULE 조회
- **DynamoDBContractRegistryAdapter.saveSinkRuleContract()**: SinkRule 저장
- **SeedContractsToDynamoDB**: LocalYamlSinkRuleRegistryAdapter → DynamoDB 시드
- **adapterModule**: LocalYamlSinkRuleRegistryAdapter (개발)
- **productionAdapterModule**: DynamoDBSinkRuleRegistryAdapter (프로덕션)
- **WorkerModule**: SinkRuleRegistryPort를 adapter/productionAdapter에서 주입

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|-----------|
| `WorkflowGraphBuilder.kt` | ContractRegistryPort, SinkRuleRegistryPort 주입, loadAllContracts 제거 |
| `AdminContractService.kt` | ContractRegistryPort 주입, loadAllContractsInternal → ContractRegistryPort |
| `ContractGraphService.kt` | ContractRegistryPort, SinkRuleRegistryPort 주입 |
| `AdminModule.kt` | AdminContractService, WorkflowGraphBuilder에 ContractRegistryPort 주입 |
| `WorkerModule.kt` | SinkRuleRegistryPort DI (이미 있음, production에서 DynamoDB 어댑터 추가 시) |

## 하위 호환성

- **개발 환경**: LocalYamlContractRegistryAdapter → 동일 동작
- **Production**: DynamoDBContractRegistryAdapter → DynamoDB에서 조회
- **테스트**: Mock 또는 LocalYaml 주입

## 완료 조건

- [x] WorkflowGraphBuilder가 ContractRegistryPort + SinkRuleRegistryPort 사용
- [x] AdminContractService가 ContractRegistryPort + SinkRuleRegistryPort 사용
- [x] ContractGraphService가 ContractRegistryPort + SinkRuleRegistryPort 사용
- [x] ContractFileRegistry 디스크 로드 제거 (WorkflowGraphBuilder, AdminContractService, ContractGraphService)
- [x] unitTest 통과
- [x] Phase 2: DynamoDBSinkRuleRegistryAdapter 구현
- [x] Phase 2: productionAdapterModule에서 DynamoDB SinkRule 사용
- [x] Phase 2: SeedContractsToDynamoDB SINK_RULE 시드 지원
