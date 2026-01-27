# ADR-0002: Determinism & Safety Enforcement Layer

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-002

---

## Context

RFC-V4-001은 구조적으로 완결된 런타임 아키텍처를 정의했지만, 라이브러리 관점에서 다음 질문들이 명시적으로 고정되지 않으면 운영 불가능했습니다:

- "같은 데이터"의 기준은 무엇인가
- hash가 다르면 언제 버그인가
- tombstone은 무엇을 의미하는가
- fan-out 순서가 결과에 영향을 주지 않는가
- 어떤 오류는 재시도해도 되는가

## Decision

**Determinism & Safety Enforcement Layer**를 도입하여 모든 결정 규칙을 SSOT로 고정합니다.

### 핵심 규칙

#### 1. Canonicalization Rules

- 모든 hash 계산 대상(JSON)은 **RFC 8785 Canonical JSON** 준수
- Object key: lexicographical sort
- Number: 정규화 표현 (1 ≠ 1.0)
- Array: 순서 보존 (DSL에서 명시된 경우에만 정렬 허용)

#### 2. Hashing Rules

- **SHA-256** 고정
- Salt 사용 금지
- Hash 입력 범위 고정:
  - RawData: `canonical(raw_payload) + schema_id + schema_version`
  - Slice: `canonical(slice_payload) + ruleset_id + ruleset_version`
  - ChangeSet: `canonical(diff_ops) + from_version + to_version + ruleset_id + ruleset_version`

#### 3. Conflict & InvariantViolation 분류

**재시도 불가 오류 (Hard Failure)**:
- 동일 (tenant, entityKey, version)에서 payload_hash 다름
- 동일 slice key에서 slice_hash 다름
- FULL_REBUILD ≠ INCREMENTAL 결과
- → 즉시 실패, 재시도 금지

**재시도 가능 오류 (Soft Failure)**:
- DynamoDB ConditionalCheckFailed (동시성)
- 네트워크 오류
- 일시적 timeout
- → 동일 입력으로 safe retry 가능

#### 4. Deterministic Ordering Rules

다음 결과물은 항상 동일한 정렬 순서:
- Slice 생성 결과 목록
- ImpactedSliceKeySet
- Inverted index key 목록
- Fan-out 처리 대상 key 집합

정렬 규칙: **lexicographical ascending**, locale 영향 금지

#### 5. Tombstone Semantics

- **Tombstone Slice**: "이 sliceType은 이 entityKey/version 조합에서 존재하지 않음"을 결정적으로 표현
- `slice_payload = null`, `tombstone = true`, `slice_hash`는 반드시 존재
- View에서 Tombstone 처리:
  - `missingPolicy = FAIL_CLOSED` → 즉시 오류
  - `missingPolicy = PARTIAL_ALLOWED` → 해당 slice 제외

#### 6. Safe Defaults

모든 기본값은 보수적으로 설정:
- `missingPolicy = FAIL_CLOSED`
- `ImpactMap onNoMatch = FAIL_CLOSED`
- `Join missing = FAIL_CLOSED`
- `Index missing = FAIL_CLOSED`

## Consequences

### Positive

- ✅ 결정성 및 멱등성 보장
- ✅ 운영 중 실수로 결정성이 깨지지 않도록 가드레일 제공
- ✅ 오류 분류로 재시도 정책 명확화
- ✅ Tombstone으로 논리적 삭제 지원

### Negative

- ⚠️ Canonicalization 오버헤드
- ⚠️ 엄격한 검증으로 인한 개발 복잡도 증가

### Neutral

- Hash 계산 비용
- 정렬 연산 비용

---

## 참고

- [RFC-V4-002](../rfc/rfc002.md) - 원본 RFC 문서
- [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785.html) - JSON Canonicalization Scheme
