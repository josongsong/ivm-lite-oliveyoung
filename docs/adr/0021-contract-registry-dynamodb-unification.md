# ADR-0021: Contract Registry DynamoDB 통합

**Status**: Accepted  
**Date**: 2026-02  
**Deciders**: Architecture Team  
**RFC**: RFC-022

---

## Context

Contract/규칙을 디스크(YAML)에서 하드코딩으로 로드하는 경로가 여러 곳에 분산되어 있었습니다:

- WorkflowGraphBuilder: ContractFileRegistry + getResourceAsStream
- AdminContractService: `/contracts/v1` 디스크 스캔
- ContractGraphService: loadDescriptorsInternal()
- LocalYamlSinkRuleRegistryAdapter: sinkrule-*.yaml

## Decision

**DynamoDB Contract Registry**를 단일 소스로 통합합니다.

### 목표

1. Contract 조회 경로를 **ContractRegistryPort** 단일화
2. Production 환경에서 **DynamoDB**가 Contract SSOT
3. 개발/테스트는 LocalYaml 유지 (DI로 전환)

### 전환 대상

| 위치 | Before | After |
|------|--------|-------|
| WorkflowGraphBuilder | ContractFileRegistry | ContractRegistryPort |
| AdminContractService | loadAllContractsInternal() | ContractRegistryPort |
| ContractGraphService | loadDescriptorsInternal() | ContractRegistryPort |
| SinkRule | LocalYamlSinkRuleRegistryAdapter | SinkRuleRegistryPort (DynamoDB) |

### DynamoDB Contract 범위

- RULESET, VIEW_DEFINITION, CHANGESET, JOIN_SPEC, SINK_RULE
- ENTITY_SCHEMA: RuleSet.entityType으로 추출 (DynamoDB 미지원)

## Consequences

### Positive

- ✅ 단일 소스로 일관성
- ✅ Production DynamoDB 기반
- ✅ 개발/테스트 LocalYaml 유연성

### Negative

- ⚠️ DynamoDB 시드/마이그레이션 필요

---

## 참고

- [RFC-022](../rfc_archive/2026-02/RFC-022-contract-registry-dynamodb-unification.md)
