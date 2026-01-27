# ADR-0001: Contract-First 아키텍처

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-001

---

## Context

이커머스 마스터 데이터를 계약(Contract) 기반, 결정적·멱등적으로 처리하는 v4 런타임 아키텍처가 필요했습니다.

기존 시스템의 문제점:
- 데이터 일관성 보장 어려움
- 증분 업데이트 복잡성
- 조인 및 팬아웃 제어 부족
- 결정성 및 멱등성 보장 미흡

## Decision

**Contract-First 아키텍처**를 채택합니다.

### 핵심 원칙

1. **Contract is Law**: RawData / RuleSet / ViewDefinition은 계약이며, 런타임은 이를 기계적으로 집행
2. **version: Long is SSOT**: 모든 저장·슬라이싱·조회는 동일한 version: Long을 기준
3. **Determinism**: 동일 입력은 항상 동일 Slice/View 결과를 생성
4. **Idempotency**: 동일 key/version 재실행은 부작용 없이 동일 결과 반환
5. **Fail-Closed by Default**: 계약 불일치, Slice 누락, 상태 위반은 기본 오류

### 아키텍처 구성

```
RawData → Slice → Virtual View → Direct Query (DynamoDB)
```

- **RawData**: Contract 기반으로 DynamoDB에 저장
- **RuleSet**: 결정적·멱등적으로 Slice 생성
- **ChangeSet**: 증분 업데이트 지원
- **Light JOIN**: Slice 생성 시 제한적 JOIN 허용
- **View**: 물리 저장 없이 Virtual Join으로 조회

### 주요 개념

- **ChangeSet**: RawData 버전 간 차이를 계약 기반으로 정규화한 변경 집합
- **ImpactedSliceSet**: ChangeSet + RuleSet으로부터 계산되는 재생성 대상 Slice 집합
- **Light JOIN**: Slice 생성 시 다른 Entity의 RawData/Slice를 읽기 전용으로 참조
- **Inverted Index**: fan-out을 scan 없이 결정적으로 계산하기 위한 보조 인덱스

## Consequences

### Positive

- ✅ 결정성 및 멱등성 보장
- ✅ 계약 기반 기계적 집행으로 일관성 확보
- ✅ 증분 업데이트 효율성
- ✅ 확장 가능한 아키텍처

### Negative

- ⚠️ 초기 계약 설계 비용 증가
- ⚠️ 계약 변경 시 마이그레이션 필요
- ⚠️ 학습 곡선 존재

### Neutral

- 계약 작성 및 관리 오버헤드
- 버전 관리 복잡도

---

## 참고

- [RFC-V4-001](../rfc/rfc001.md) - 원본 RFC 문서
- [RFC-V4-002](../rfc/rfc002.md) - Determinism & Safety Enforcement
- [RFC-V4-003](../rfc/rfc003.md) - Contract Enhancement & DX
