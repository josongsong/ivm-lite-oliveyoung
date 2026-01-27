# ADR-0003: Contract Enhancement & DX Endgame

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-003

---

## Context

v4/v4.1 아키텍처의 계약(Contract) 레이어를 보완하고, 개발자 경험(DX)을 "끝판" 수준으로 끌어올리는 설계가 필요했습니다.

문제점:
- 계약에서 빠지기 쉬운 구멍 존재
- 계약 작성 → 타입/SDK 자동 생성 → 로컬 시뮬레이션 → 배포까지 원클릭 불가
- 계약 변경 시 호환성 검증이 PR 단계에서 자동 차단되지 않음
- 운영 입력(Replay/Backfill)이 인간 텍스트 입력으로 처리됨

## Decision

**Contract Enhancement & DX Endgame**을 구현하여 계약 레이어를 보완하고 DX를 향상시킵니다.

### 계약 보완 포인트 (12개 필수)

1. **ContractRef 표준화**: 모든 계약 참조를 구조체로 고정
2. **ContractStatusGate 필수**: 런타임 fail-closed (ACTIVE만 기본 허용)
3. **Canonicalization 규칙을 계약으로 고정**: RFC8785 프로파일 명시
4. **EntityKey/Version 규칙을 계약으로 고정**: 포맷 및 버전 타입 명시
5. **SliceKey 규칙을 계약으로 고정**: 패턴 템플릿 문자열로 정의
6. **ChangeSet 계약에 ImpactMap 포함**: 1급 시민으로 포함
7. **RuleSet에 JoinSpec 포함**: 가벼운 조인을 계약으로 명시
8. **ViewDefinition에 Policy 명시**: MissingPolicy + PartialPolicy + FallbackPolicy
9. **Tombstone 계약 고정**: 공통 필드로 고정
10. **Event Contract에서 doc_id/idempotency 스키마 고정**: Sink 멱등성 보장
11. **Registry에 Compatibility Test Artifact 저장**: 호환성 검증 아티팩트
12. **ReplayRequestContract를 운영 입력 SSOT로 고정**: 계약 파일로만 실행

### DX 목표 6개

1. **계약 작성하면 자동으로 타입/SDK 생성**
2. **계약 변경하면 compatibility gate가 PR에서 깨짐**
3. **로컬에서 end-to-end를 한 커맨드로 재현**
4. **slice/view 결과가 해시로 고정되어 diff 가능**
5. **replay/backfill이 계약 파일로만 실행**
6. **"무슨 계약이 실제로 쓰였는지"가 항상 추적 가능**

### 반드시 제공할 DX 툴링

- `ivm-lite validate-contracts`: 계약 파일 검증
- `ivm-lite codegen`: 계약에서 Kotlin SDK + JSON Schema 타입 생성
- `ivm-lite simulate`: 로컬에서 RawData → ChangeSet → Slices → View 시뮬레이션
- `ivm-lite diff`: slice_hash/view_hash 비교
- `ivm-lite replay`: ReplayRequestContract 기반 실행

### 라이브러리 기능 9개

1. Contract Loader 캐시 + hash pinning
2. Canonical JSON + hash utility SSOT
3. Deterministic ordering library
4. SingleFlight (동일 entityKey/version 동시 실행 dedupe)
5. Conditional Put wrapper (멱등성 표준)
6. Golden test runner (계약에 포함된 예제 자동 실행)
7. Property test kit (FULL==INCREMENTAL)
8. ReplayRequest validator (운영 입력 계약)
9. Explain output (DX 핵심)

## Consequences

### Positive

- ✅ 계약에서 빠지기 쉬운 구멍을 정의로 봉인
- ✅ 계약 작성 → 타입/SDK 자동 생성 → 로컬 시뮬레이션 → 배포까지 원클릭
- ✅ 계약 변경 시 호환성 검증이 PR 단계에서 자동 차단
- ✅ 운영 입력(Replay/Backfill)도 계약으로만 허용
- ✅ "무슨 계약이 실제로 쓰였는지"가 항상 추적 가능

### Negative

- ⚠️ 초기 계약 설계 및 도구 개발 비용 증가
- ⚠️ 계약 스키마 복잡도 증가
- ⚠️ 학습 곡선 존재

### Neutral

- 계약 관리 오버헤드
- DX 도구 유지보수 비용

---

## 참고

- [RFC-V4-003](../rfc/rfc003.md) - 원본 RFC 문서
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
