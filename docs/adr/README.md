# ADR — Architecture Decision Records

이 디렉토리는 ivm-lite 프로젝트의 주요 아키텍처 결정사항을 기록합니다.

## 목적

ADR은 중요한 아키텍처 결정사항을 문서화하여:
- 결정 배경과 맥락을 명확히 함
- 결정 사항과 결과를 추적 가능하게 함
- 향후 유사한 결정 시 참고 자료로 활용

## 형식

각 ADR은 다음 형식을 따릅니다:

- **Status**: Accepted / Proposed / Deprecated / Superseded
- **Date**: 결정 날짜
- **Deciders**: 결정 주체
- **RFC**: 관련 RFC 문서 참조
- **Context**: 결정 배경 및 문제 상황
- **Decision**: 채택한 결정 사항
- **Consequences**: 결정의 긍정적/부정적 영향

## ADR 목록

| 번호 | 제목 | 상태 | 날짜 | RFC |
|------|------|------|------|-----|
| [ADR-0001](./0001-contract-first-architecture.md) | Contract-First 아키텍처 | Accepted | 2026-01-27 | RFC-V4-001 |
| [ADR-0002](./0002-determinism-safety-enforcement.md) | Determinism & Safety Enforcement Layer | Accepted | 2026-01-27 | RFC-V4-002 |
| [ADR-0003](./0003-contract-enhancement-dx.md) | Contract Enhancement & DX Endgame | Accepted | 2026-01-27 | RFC-V4-003 |
| [ADR-0004](./0004-replay-backfill-operations.md) | Replay / Backfill Operational Playbook | Accepted | 2026-01-27 | RFC-V4-004 |
| [ADR-0005](./0005-domain-sliced-hexagonal-architecture.md) | Domain-sliced Package Layout + In-domain Hexagonal Architecture | Accepted | 2026-01-27 | RFC-V4-005 |
| [ADR-0006](./0006-slicing-join-scope.md) | Slicing Join 허용 범위 및 중간 IR 정의 | Accepted | 2026-01-27 | RFC-V4-006 |
| [ADR-0007](./0007-sink-orchestration.md) | IVM Sink Orchestration, Triggering, and Plugin-based Delivery | Accepted | 2026-01-27 | RFC-V4-007 |
| [ADR-0008](./0008-deploy-orchestration-fluent-dx.md) | Deploy Orchestration Law & Fluent DX | Accepted | 2026-01-27 | RFC-V4-008 |
| [ADR-0009](./0009-rule-driven-compiler.md) | Rule-Driven Compiler Architecture | Accepted | 2026-01-27 | RFC-V4-009 |
| [ADR-0010](./0010-repository-layout-naming.md) | Repository Layout, Naming, and Guardrails | Accepted | 2026-01-27 | RFC-V4-010 |
| [ADR-0011](./0011-contract-versioning-strategy.md) | Contract 버전 관리 전략 | Accepted | 2026-01-27 | RFC-012 |
| [ADR-0012](./0012-projection-mathematical-correctness.md) | Projection 기능 수학적 정합성 검증 | Accepted | 2026-01-29 | - |
| [ADR-0013](./0013-projection-end-to-end-verification.md) | Projection End-to-End 검증 | Accepted | 2026-01-29 | - |
| [ADR-0014](./0014-doc001-flat-catalog-onboarding.md) | DOC-001 상품 마스터 — Flat Catalog 온보딩 | Accepted | 2026-01-26 | RFC-IR-031 |
| [ADR-0015](./0015-sink-plugin-architecture.md) | Sink Plugin Architecture & IaC | Accepted | 2026-02-12 | RFC-017 |
| [ADR-0016](./0016-slice-execution-order-enforcement.md) | Slice 실행 순서/의존성 런타임 강제 | Accepted | 2026-02 | RFC-018 |
| [ADR-0017](./0017-sdk-driven-architecture.md) | SDK-Driven Architecture — Outbox 제거 | Proposed | 2026-02-12 | RFC-018 |
| [ADR-0018](./0018-external-sdk-integration.md) | External SDK Integration | Proposed | 2026-02-12 | RFC-019 |
| [ADR-0019](./0019-sink-reliability-and-consistency.md) | Sink Reliability & Data Consistency | Proposed | 2026-02-16 | RFC-020 |
| [ADR-0020](./0020-sota-dsl-dx.md) | SOTA DSL DX — Developer Experience | Proposed | 2026-02 | RFC-021 |
| [ADR-0021](./0021-contract-registry-dynamodb-unification.md) | Contract Registry DynamoDB 통합 | Accepted | 2026-02 | RFC-022 |
| [ADR-0022](./0022-product-schema-dx.md) | Product Schema DX | Accepted | 2026-02 | product-schema-dx |

## RFC와의 관계

이 ADR들은 원본 RFC 문서들을 기반으로 작성되었습니다:

- **RFC-V4-001 ~ RFC-V4-010**: 핵심 아키텍처 결정사항 (rfc_archive)
- **RFC-017 ~ RFC-022**: Sink, SDK, Contract 등 (rfc_archive/2026-02)
- 각 ADR은 해당 RFC의 핵심 결정사항을 요약

## 참고

- [RFC 아카이브](../rfc_archive/) - 원본 RFC 문서들
- [RFC 2026-02](../rfc_archive/2026-02/) - 최근 RFC (017~022, product-schema-dx)
- [Architecture Onboarding](../guides/architecture-onboarding.md) - 아키텍처 개요
