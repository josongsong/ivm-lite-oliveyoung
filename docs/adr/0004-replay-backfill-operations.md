# ADR-0004: Replay / Backfill Operational Playbook

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-004

---

## Context

v4/v4.1 아키텍처에서 Replay / Backfill은 장애 대응 수단이 아니라 정상 운영 절차입니다.

다음이 기계적으로 보장되어야 합니다:
- 언제 replay/backfill이 필요한지
- 무엇을 SSOT로 삼아 다시 계산하는지
- 어느 레이어에서 실행되는지
- 허용되는 replay와 금지되는 replay의 경계
- 운영 중 실수로 결정성·멱등성이 깨지지 않도록 하는 가드레일

## Decision

**Replay / Backfill Operational Playbook**을 정의하여 운영 절차를 표준화합니다.

### SSOT 원칙 (Non-Negotiable)

1. **SSOT는 항상 v4 Runtime 결과물(Slice)**
   - Sink(OpenSearch)는 절대 SSOT 아님
   - Sink 단독 replay/backfill 금지

2. **Replay는 계산이고, Sink는 반영이다**
   - 계산은 v4 Runtime에서만 수행
   - Sink는 CDC 이벤트를 받아 반영만 수행

3. **FULL == INCREMENTAL 불변식 유지**
   - Replay / Backfill에서도 반드시 성립해야 함

### Replay / Backfill 분류표

| 구분 | 목적 | 허용 | 실행 위치 |
|------|------|------|-----------|
| Slice Replay | 동일 결과 재생성 | 허용 | v4 Runtime |
| ChangeSet Replay | 증분 검증 | 허용 | v4 Runtime |
| Sink Replay | Sink 재적재 | 허용 | v4 Runtime → CDC |
| Sink-only Replay | Sink만 재생성 | ❌ 금지 | N/A |
| Cross-Version Replay | 비연속 version | ❌ 금지 | N/A |
| RuleSet 변경 Backfill | 새 RuleSet 적용 | 허용 | v4 Runtime |

### Replay 트리거 시나리오

**반드시 Replay가 필요한 경우**:
- Sink(OpenSearch) 인덱스 손상/삭제
- CDC 장애로 이벤트 누락
- Index mapping 변경
- Inverted index 규칙 변경
- 운영 실수로 Sink 데이터 불일치 발생

**Replay가 필요 없는 경우**:
- v4 Runtime 정상, Sink 지연만 존재
- 일시적 CDC 실패 후 자동 재시도 성공
- 동일 slice_hash로 중복 write 발생

### Replay Execution Modes

#### Partial Replay (기본)

대상 범위를 명시적으로 제한:
- tenantId
- entityKey (list / prefix / range 중 1개만 허용)
- version range (from / to 필수)
- sliceType allowlist
- ruleSetRef

❌ 무제한 전체 replay 금지

#### Full Replay (예외적)

허용 조건:
- Sink 전체 재구축
- 대규모 계약(backfill) 변경

절차:
1. Blue/Green 기준 new index 생성
2. v4 Runtime FULL slicing
3. CDC 통해 전체 재전파
4. alias swap
5. old index 보존 후 삭제

### Replay Request Contract (P0)

Replay 요청은 반드시 계약 형태로만 허용:

```yaml
replayId: uuid
reason: string
tenantId: T1
scope:
  entityKeys: [product#P1, product#P2]   # OR prefix OR range
  versionRange:
    from: 40
    to: 42
  sliceTypes: [PRODUCT_CORE, PRODUCT_PRICE]
ruleSetRef: product-core@1.3.0
verifyMode: STRICT | SAMPLING | HASH_ONLY
cutlineVersion: 42
```

계약 미준수 시 Validator 단계에서 즉시 차단.

### VerifyMode 규칙 (P0)

- **STRICT**: FULL vs INCREMENTAL 전수 비교 (Full replay / 대규모 backfill 필수)
- **SAMPLING**: n% 샘플 + slice_hash 비교 (중규모 partial replay)
- **HASH_ONLY**: hash 동일성만 확인 (소규모 재전파)

### Replay Execution Flow

```
[Operator]
  → ReplayRequestContract
    → Replay Validator
      → v4 Runtime (slicing / changeset)
        → DynamoDB (Slice overwrite, 동일 hash)
          → DynamoDB Streams
            → CDC Dispatcher
              → Sink Adapter (OpenSearch)
```

**중요**: Sink는 항상 마지막 단계, 중간 단계를 건너뛰는 replay 금지

### Idempotency & Concurrency

- Replay는 동일 입력 재실행 가능
- 중복 CDC 이벤트 허용
- Sink write는 doc_id 기반 idempotent
- 동일 (tenant, entityKey, version, ruleSet)는 single-flight

### Failure & Rollback Policy

- Replay 중 실패 시 즉시 중단
- 이미 반영된 Sink write는 롤백하지 않음
- 재시도는 동일 replay_id로 수행
- Rollback은 개념적으로 존재하지 않음 (항상 replay로 복구)

## Consequences

### Positive

- ✅ Replay / Backfill은 정상 운영 기능
- ✅ SSOT는 항상 v4 Runtime
- ✅ Sink는 절대 복구의 출발점이 아님
- ✅ 결정성은 운영 절차에서도 깨지지 않음
- ✅ 계약 기반으로 운영 실수 방지

### Negative

- ⚠️ ReplayRequestContract 작성 필요
- ⚠️ VerifyMode 선택 복잡도
- ⚠️ Full Replay 시 리소스 소비

### Neutral

- Replay 실행 시간
- 검증 오버헤드

---

## 참고

- [RFC-V4-004](../rfc/rfc004.md) - 원본 RFC 문서
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
- [RFC-V4-003](../rfc/rfc003.md) - Contract Enhancement
