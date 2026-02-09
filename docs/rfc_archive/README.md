# RFC 문서

이 디렉토리는 ivm-lite 프로젝트의 Request for Comments (RFC) 문서들을 포함합니다.

## 상태

**중요**: 이 RFC 문서들은 ADR (Architecture Decision Records)로 마이그레이션되었습니다.

- **원본 RFC**: 상세한 설계 문서로 보존 (참고용)
- **ADR**: 핵심 아키텍처 결정사항을 요약한 최종 결정 문서

**새로운 아키텍처 결정사항을 찾으실 때는 [ADR 문서](../adr/)를 먼저 참고하세요.**

## RFC 목록

### 핵심 아키텍처 RFC (ADR로 마이그레이션됨)

| RFC | 제목 | ADR | 상태 |
|-----|------|-----|------|
| [RFC-V4-001](./rfc001.md) | RawData → Slice → Virtual View → Direct Query | [ADR-0001](../adr/0001-contract-first-architecture.md) | ✅ Accepted |
| [RFC-V4-002](./rfc002.md) | Determinism & Safety Enforcement Layer | [ADR-0002](../adr/0002-determinism-safety-enforcement.md) | ✅ Accepted |
| [RFC-V4-003](./rfc003.md) | Contract Enhancement & DX Endgame | [ADR-0003](../adr/0003-contract-enhancement-dx.md) | ✅ Accepted |
| [RFC-V4-004](./rfc004.md) | Replay / Backfill Operational Playbook | [ADR-0004](../adr/0004-replay-backfill-operations.md) | ✅ Accepted |
| [RFC-V4-005](./rfc005.md) | Domain-sliced Package Layout + In-domain Hexagonal Architecture | [ADR-0005](../adr/0005-domain-sliced-hexagonal-architecture.md) | ✅ Accepted |
| [RFC-V4-006](./rfc006.md) | Slicing Join 허용 범위 및 중간 IR 정의 | [ADR-0006](../adr/0006-slicing-join-scope.md) | ✅ Accepted |
| [RFC-V4-007](./rfc007.md) | IVM Sink Orchestration, Triggering, and Plugin-based Delivery | [ADR-0007](../adr/0007-sink-orchestration.md) | ✅ Accepted |
| [RFC-V4-008](./rfc008.md) | Deploy Orchestration Law & Fluent DX | [ADR-0008](../adr/0008-deploy-orchestration-fluent-dx.md) | ✅ Accepted |
| [RFC-V4-009](./rfc009.md) | Rule-Driven Compiler Architecture | [ADR-0009](../adr/0009-rule-driven-compiler.md) | ✅ Accepted |
| [RFC-V4-010](./rfc010.md) | Repository Layout, Naming, and Guardrails | [ADR-0010](../adr/0010-repository-layout-naming.md) | ✅ Accepted |
| [RFC-012](./rfc012-contract-versioning-strategy.md) | Contract 버전 관리 전략 | [ADR-0011](../adr/0011-contract-versioning-strategy.md) | ✅ Accepted |

### 구현 가이드 RFC

| RFC | 제목 | 상태 | 설명 |
|-----|------|------|------|
| [RFC-011](./rfc011-doc001-flat-catalog.md) | DOC-001 상품 마스터 온보딩 | ✅ Accepted | DOC-001 상품 마스터 데이터 온보딩 가이드 |
| [RFC-IMPL-001](./rfcimpl001.md) | Implementation Guide 001 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-002](./rfcimpl002.md) | Implementation Guide 002 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-003](./rfcimpl003.md) | Implementation Guide 003 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-004](./rfcimpl004.md) | Implementation Guide 004 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-005](./rfcimpl005.md) | Implementation Guide 005 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-006](./rfcimpl006.md) | Implementation Guide 006 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-007](./rfcimpl007.md) | Implementation Guide 007 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-008](./rfcimpl008.md) | Implementation Guide 008 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-009](./rfcimpl009.md) | Implementation Guide 009 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-010](./rfcimpl010.md) | Implementation Guide 010 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-011](./rfcimpl011.md) | Implementation Guide 011 | ✅ Accepted | 구현 가이드 |
| [RFC-IMPL-012](./rfcimpl012-sink-config.md) | Sink Configuration | ✅ Accepted | Sink 설정 가이드 |
| [RFC-IMPL-013](./rfcimpl013-inverted-index-consolidation.md) | Inverted Index Consolidation | ✅ Accepted | Inverted Index 통합 가이드 |

### 기타 문서

| 문서 | 제목 | 상태 | 설명 |
|------|------|------|------|
| [IMPLEMENTATION_ROADMAP](./IMPLEMENTATION_ROADMAP.md) | Implementation Roadmap | ✅ Active | 구현 로드맵 |
| [TODO-schema-registry-sota](./TODO-schema-registry-sota.md) | Schema Registry SOTA | ⏳ TODO | Schema Registry 최신 상태 |
| [wave5k-review](./wave5k-review.md) | Wave 5K Review | ✅ Complete | Wave 5K 리뷰 |

## RFC vs ADR

### RFC (Request for Comments)
- **목적**: 상세한 설계 문서, 기술적 세부사항, 구현 가이드
- **대상**: 개발자, 아키텍트, 구현 담당자
- **내용**: 배경, 문제 정의, 상세 설계, 예시, 구현 계획 등

### ADR (Architecture Decision Record)
- **목적**: 핵심 아키텍처 결정사항의 요약 및 결정 근거
- **대상**: 모든 이해관계자 (개발자, 관리자, 신규 팀원)
- **내용**: Context, Decision, Consequences 중심

## 사용 가이드

### 새로운 아키텍처 결정을 찾을 때

1. **먼저 [ADR](../adr/) 확인**: 핵심 결정사항을 빠르게 파악
2. **상세 내용이 필요하면 RFC 확인**: 기술적 세부사항 및 구현 가이드
3. **구현 시 Implementation Guide 참고**: 실제 구현 방법 및 예시

### 아키텍처 결정을 문서화할 때

1. **RFC 작성**: 상세한 설계 문서 작성
2. **ADR 작성**: 핵심 결정사항을 ADR 형식으로 요약
3. **README 업데이트**: 매핑 관계 업데이트

## 참고

- [ADR 문서](../adr/) - 아키텍처 결정 기록
- [Architecture Onboarding](../architecture-onboarding.md) - 아키텍처 개요
- [SDK Guide](../sdk-guide.md) - SDK 사용 가이드
