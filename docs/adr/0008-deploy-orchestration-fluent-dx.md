# ADR-0008: Deploy Orchestration Law & Fluent DX

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-008

---

## Context

IVM 엔진에서 Deploy 실행 방식, Outbox 사용 시점/방식, 상태 머신, Swap 법칙, Fluent SDK DX를 하나의 SSOT로 고정해야 했습니다.

요구사항:
- IVM은 계약된 Raw 데이터만 입력으로 받음
- 노출은 오직 deploy를 통해서만 발생
- Deploy 실행은 Compile / Ship / Cutover 3축 선택으로 표현
- slicing을 비동기화하면 ship은 자연스럽게 연쇄 outbox가 됨
- Outbox는 "재시도"가 아니라 실행 분리·확장·관측을 위한 SSOT
- SDK는 contract-registry 기반 codegen으로 IDE가 문서가 되도록 설계

## Decision

**Deploy Orchestration Law & Fluent DX**를 정의합니다.

### 개념 정의

#### Compile

**정의**: snapshot 생성, diff/impact 계산, slicing 수행하는 결정적 내부 계산 단계

- Canonical Snapshot 생성
- ChangeSet 계산
- Slice 빌드
- Inverted Index 업데이트

**결정성 보장**: 동일 입력 → 동일 출력 보장, 시간/랜덤 금지

#### Ship

**정의**: slicing 산출물을 외부 시스템으로 전파하는 side-effect 단계

- Sink Orchestration 실행
- SinkPlugin이 Slice/View를 읽어서 외부 시스템에 전달
- OpenSearch / Reco / 기타 sink

**멱등성 보장**: doc_id 기반 idempotent, 동일 taskId 재실행 시 동일 결과

#### Cutover (Swap)

**정의**: storefront가 읽는 active pointer를 신규 산출물로 전환하는 단계 (데이터 이동 없음, pointer swap)

- active_version 포인터 업데이트
- active_version은 외부 포인터이며 SSOT 아님

### 실행 축 (SSOT)

Deploy는 다음 3축 조합으로 정의:

#### Compile 축

- **compile.sync()**: 요청 흐름에서 slicing까지 동기 수행
- **compile.async()**: slicing을 outbox job으로 분리

#### Ship 축

- **ship.sync {}**: 외부 sink까지 요청 흐름에서 동기 수행
- **ship.async {}**: sink를 outbox로 전파

#### Cutover 축

- **cutover.ready()** (기본): READY 도달 즉시 swap
- **cutover.done()** (옵션): ship까지 전부 성공 후 swap

### 허용 조합 규칙 (강제 불변식, P0)

| Compile | Ship | 허용 | 이유 |
|---------|------|------|------|
| sync | sync | ⭕ 가능 | 산출물 존재 |
| sync | async | ⭕ 가능 | 산출물 존재 |
| async | async | ⭕ 가능 | worker 체인 |
| async | sync | ❌ 불가 | 산출물 준비 전 동기 ship 불가 |

**SDK 레벨에서 compile.async + ship.sync는 타입으로 차단함.**

### Deploy 상태 머신 (SSOT)

**States**:
- **QUEUED**: compile/ship job이 outbox에 기록됨
- **RUNNING**: compile 수행 중
- **READY**: slicing 완료, swap 가능
- **SINKING**: ship 수행 중
- **DONE**: deploy 완료
- **FAILED**: 실패(재시도 가능)

**기본 흐름 (cutover.ready)**:
```
QUEUED → RUNNING → READY → SINKING → DONE
                    ↘ FAILED
```

### Cutover (Swap) 법칙

#### READY Cutover (Default)

- **정의**: READY 도달 즉시 swap
- **동작**: storefront는 즉시 최신 slice 사용, ship은 이후 진행 (비동기)
- **DONE 정의**: swap 완료 + ship job queued(있다면)

#### DONE Cutover (Optional)

- **정의**: ship까지 전부 성공 후 swap
- **동작**: Compile + Ship 모두 완료 후 active_version 업데이트
- **DONE 정의**: ship 성공 + swap 완료
- **사용 사례**: 외부 시스템(Sink)과의 정합성이 중요한 경우

### Outbox 사용 정책 (정확한 시점, P0)

**Outbox가 사용되는 경우**:
- **compile.async()** → COMPILE_TASK outbox 기록
- **ship.async()** → SHIP_TASK outbox 기록
- compile async 완료 후 ship이 있다면 연쇄 outbox 생성

**Outbox를 쓰지 않는 경우**:
- **compile.sync()** 단계
- **ship.sync()** 단계

👉 **Outbox는 비동기 경계가 생기는 순간에만 사용됨.**

### Fluent SDK DX (Contract Codegen)

#### Raw 입력은 codegen DSL만 허용

```kotlin
Ivm.client()
  .ingest()
  .product {
    sku("ABC-123")
    name("Moisture Cream")
    price(19000)
    currency("KRW")
  }
```

**product {}** 는 Contract Registry 기반 codegen 산물
- 문자열 엔티티/스키마 금지: 타입 안전성 보장

#### Contract Registry 기반 Codegen

- Contract Registry에서 RuleSet 로드
- EntityType별 DSL 생성
- 타입 안전성 보장
- IDE 지원: 자동완성, 타입 체크, 문서화

### Deploy DX — 가장 직관적인 표현

#### 기본값 (정석)

```kotlin
Ivm.client()
  .ingest()
  .product { ... }
  .deploy {
    ship.async {
      opensearch()
      personalize()
    }
  }
```

**동작**: Compile 동기 수행, Cutover READY 도달 즉시 swap, Ship 비동기 수행 (outbox)

#### 전부 즉시

```kotlin
Ivm.client()
  .ingest()
  .product { ... }
  .deploy {
    compile.sync()
    ship.sync {
      opensearch()
      personalize()
    }
  }
```

**동작**: Compile 동기 수행, Ship 동기 수행, Cutover Ship 완료 후 swap

#### 대형 배포 잡

```kotlin
val job = Ivm.client()
  .ingest()
  .product { ... }
  .deployAsync {
    compile.async()
    ship.async {
      opensearch()
      personalize()
    }
  }

Ivm.client().deploy.status(job.id)
```

**동작**: Compile 비동기 수행 (outbox), Ship 비동기 수행 (outbox, 연쇄), Cutover READY 도달 즉시 swap (기본값)

### DX Shortcut API (권장)

- **deployNow { ... }**: compile sync + ship async
- **deployNowAndShipNow { ... }**: compile sync + ship sync
- **deployQueued { ... }**: compile async + ship async

## Consequences

### Positive

- ✅ Deploy는 compile / ship / cutover 3축으로만 표현되어 명확한 책임 분리
- ✅ 기본값은 compile.sync + ship.async + cutover.ready로 가장 일반적인 사용 사례
- ✅ Outbox는 비동기 경계에서만 사용되어 실행 분리·확장·관측을 위한 SSOT
- ✅ SDK는 contract codegen Fluent DSL로 고정되어 IDE가 문서가 되도록 설계
- ✅ 불가능한 조합은 SDK 타입 단계에서 차단

### Negative

- ⚠️ 3축 조합 이해 필요
- ⚠️ Outbox 사용 시점 판단 필요
- ⚠️ Contract Registry 기반 Codegen 개발 비용

### Neutral

- Deploy 실행 시간
- 상태 머신 관리 오버헤드

---

## 참고

- [RFC-V4-008](../rfc/rfc008.md) - 원본 RFC 문서
- [RFC-V4-007](../rfc/rfc007.md) - Sink Orchestration
- [RFC-V4-003](../rfc/rfc003.md) - Contract Enhancement
