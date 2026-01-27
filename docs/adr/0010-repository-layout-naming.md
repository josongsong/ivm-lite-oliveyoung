# ADR-0010: Repository Layout, Naming, and Guardrails

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-010

---

## Context

ivm-lite 레포의 **폴더 구조·네이밍·경계(가드레일)** 를 SSOT로 고정해야 했습니다.

문제점:
- v0에서 빠르게 개발하면서도 도메인 분리의 "되돌리기 어려움"을 제거해야 함
- 도메인 간 의존이 섞이면 증분/슬라이싱/뷰/싱크 확장 시 변경 비용 폭발
- AI/협업 개발에서 "어디에 코드를 둬야 하는가"가 애매하면 토큰/시간이 누수됨

## Decision

**Orchestration-First Entry + Domain-Only Meaning (SOTA Conventions)** 구조를 채택합니다.

### 핵심 원칙

1. **외부 진입점은 orchestration만 호출한다** (apps/cli/worker는 트리거)
2. **cross-domain workflow는 orchestration만 소유한다**
3. **orchestration은 도메인 기능(service/port/step)을 조합**한다. UseCase-of-UseCase는 최소화하고 호출 깊이를 제한한다.
4. **비즈니스 의미/정책은 domain/service에만 둔다.** orchestration은 흐름·경계·실행 정책만 소유한다.
5. **호출되는 단위는 "UseCase"가 아니라 Step/Activity/Command/Task로 의미를 분리한다.**

### 최상위 레포 구조

```
ivm-lite/
  docs/
    rfc/
    adr/
  
  src/main/kotlin/com/oliveyoung/ivmlite/
    shared/                      # 공통 코어(결정성/에러/타입/공통 포트)
    package/                     # 도메인 그룹
      rawdata/
      changeset/
      contracts/
      slices/
      orchestration/             # cross-domain workflow + steps (SSOT)
    tooling/                     # DX 도구
    apps/                        # 트리거/엔트리포인트
      runtimeapi/                # (추후) http/worker 등
      opscli/                    # 운영 CLI
```

### `shared/` 규칙 (P0)

**shared가 포함할 수 있는 것(허용)**:
- 결정성 유틸: canonical json, hashing, deterministic ordering
- 공통 에러 모델: DomainError 계열
- 공통 타입: TenantId, EntityKey, SemVer 등
- 공통 포트: SingleFlightPort, ClockPort/ObservabilityPort/TenantValidatorPort 등

**shared가 포함하면 안 되는 것(금지)**:
- rawdata/changeset/slices/view/sink의 "의미 로직"
- 특정 도메인의 정책/규칙/워크플로우

### `package/<domain>/` 구조 규칙

**레이어 고정**:
- `domain/`: 순수 의미 모델 + 불변식 + 순수 계산
- `ports/`: 외부 의존 계약(인터페이스)
- `adapters/`: 인프라 구현 (DB/registry/messaging 등)
- `application/`: **single-domain façade**(선택)
  - 외부 진입점 아님 (orchestration에서만 호출)

**도메인 간 import 금지 (P0)**:
- `package/rawdata/**` 가 `package/slices/**` 를 직접 import 금지
- 도메인 간 호출/데이터 접근은 **ports 계약**을 통해서만

### `package/orchestration/` 규칙 (P0)

**목적**:
- **cross-domain workflow의 SSOT**
- 관측/트랜잭션 경계/timeout/retry/compensation의 SSOT

**내부 구조(권장)**:

**v0 단일 모듈(현재)**: orchestration은 hexagonal 계층을 갖지 않음

```
package/orchestration/
  ├── IngestWorkflow.kt
  ├── SlicingWorkflow.kt
  └── QueryViewWorkflow.kt
```

**v1+ 이후: 필요해지면 구조 추가**

```
package/orchestration/
  application/
    *Workflow.kt          # 외부 진입점
  steps/
    *Step.kt              # 내부 step (재사용 단위)
  domain/                 # (필요시) 정책/규칙/상태 머신
  ports/                  # (필요시) orchestration 전용 포트
```

**원칙**: 복잡도가 실제로 생기기 전까지 폴더 구조를 추가하지 않는다 (YAGNI).

**네이밍 규칙(강제)**:
- **v0 (현재)**: 파일명 `*Workflow.kt` (외부 진입점)
- **v1+ (필요시)**: 외부 진입점 `*Workflow` 또는 `*Orchestration`, 내부 step `*Step` / `*Activity` / `*Command`

**UseCase-of-UseCase 최소화(강제)**:
- orchestration → orchestration 호출 금지
- workflow가 다른 workflow를 호출하는 중첩 금지 (깊이 제한)
- 권장 깊이: `workflow -> step/service`, `step -> domain service/port`, 그 이상 중첩 금지

**"정책의 위치" 규칙(권장)**:
- 도메인 정책(의미/규칙): domain/service 쪽
- 실행 정책(timeout/retry/backoff/compensation/transaction): orchestration/step 쪽

### `apps/` 규칙 (P0)

**역할**:
- 외부 트리거(HTTP/CLI/worker/event)를 받아 **orchestration workflow를 호출**
- DI(wiring)와 직렬화/역직렬화, 인증/인가 같은 "어댑터" 책임만

**금지 사항**:
- apps에서 비즈니스 의미 로직 구현 금지
- apps에서 도메인 간 조합을 직접 구현 금지 (반드시 orchestration으로)

### `tooling/` 규칙

**역할**:
- 개발/테스트/CI 단계에서만 사용하는 도구 제공
- 런타임 경로의 SSOT가 아님 (런타임 로직 복제 금지)

**예시**:
- `validate-contracts`: 계약 파일 검증
- (추후) codegen/simulate/diff/replay

### Naming Convention (SSOT)

**패키지/디렉토리**:
- 최상위: `shared`, `package`, `tooling`, `apps`
- 도메인: 단수 소문자 (rawdata, changeset, contracts, slices, orchestration)

**클래스**:
- **도메인**:
  - domain: 명사/값 객체 중심 (`RawDataRecord`, `SliceRecord`)
  - ports: `*Port` 접미 (`RawDataRepositoryPort`)
  - adapters: `*Adapter` 또는 기술명 접미 (`InMemoryRawDataRepository`)
- **Orchestration**:
  - v0 (현재): `*Workflow` (IngestWorkflow, SlicingWorkflow, QueryViewWorkflow)
  - v1+ (필요시): 외부 진입 `*Workflow`, 내부 step `*Step` / `*Activity` / `*Command`

### Guardrails (Enforcement)

**코드 리뷰 체크리스트 (P0)**:
- cross-domain 조합이 `package/orchestration` 밖에 존재하는가? → 즉시 이동
- 도메인 간 직접 import가 생겼는가? → 즉시 차단
- apps가 orchestration 대신 도메인을 직접 조합하는가? → 즉시 차단

**정적 분석(추후, 권장)**:
- Detekt/ktlint 커스텀 룰로 "금지 import"를 기계적으로 막는다
- 예: `package/<domain>/`에서 다른 `package/<domain2>/` import 금지
- 예: orchestration → orchestration import/호출 금지
- 예: apps → domain 직접 호출/조합 금지 (orchestration만 호출)

## Consequences

### Positive

- ✅ cross-domain 결합을 사전에 차단
- ✅ 관측/트랜잭션/재시도 같은 실행 정책을 한 곳에서 통제
- ✅ 팀이 확장 가능한 구조로 일관되게 개발할 수 있도록 함
- ✅ "여기엔 뭘 둬야 하지?"를 없애서 개발 속도와 품질을 동시에 올림

### Negative

- ⚠️ 초기 구조 설계 비용
- ⚠️ orchestration 레이어 추가로 인한 복잡도
- ⚠️ 도메인 간 통신은 ports를 통해서만 가능하여 간접적

### Neutral

- 패키지 구조 복잡도
- Guardrails 유지보수

---

## 참고

- [RFC-V4-010](../rfc/rfc010.md) - 원본 RFC 문서
- [RFC-V4-005](../rfc/rfc005.md) - Domain-sliced Architecture
