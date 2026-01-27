# ADR-0005: Domain-sliced Package Layout + In-domain Hexagonal Architecture

**Status**: Accepted  
**Date**: 2026-01-27  
**Deciders**: Architecture Team  
**RFC**: RFC-V4-005

---

## Context

ivm-lite 라이브러리의 패키지 레이아웃과 아키텍처 구조를 정의해야 했습니다.

문제점:
- v0에서 빠르게 개발하면서도 도메인 분리의 "되돌리기 어려움"을 제거해야 함
- 도메인 간 의존이 섞이면 증분/슬라이싱/뷰/싱크 확장 시 변경 비용 폭발
- AI/협업 개발에서 "어디에 코드를 둬야 하는가"가 애매하면 토큰/시간이 누수됨

## Decision

**Domain-sliced + In-domain Hexagon 구조**를 SSOT로 고정합니다.

### 핵심 원칙

1. **Domain-sliced 우선**: 최상위는 도메인 단위 디렉터리로 슬라이싱
2. **In-domain Hexagon 고정**: 각 도메인 내부에 헥사고날(domain/application/ports/adapters) 배치
3. **shared 최소화**: 결정성/타입/공통 포트/공통 wiring만 허용

### 최상위 패키지 구조

```
ivm-lite/
  src/main/kotlin/com/oliveyoung/ivmlite/
    shared/                      # 공통 인프라/타입
      domain/
        determinism/
        errors/
        types/
      ports/
      adapters/
      wiring/
    
    package/                     # 도메인들 그룹화
      rawdata/
      changeset/
      contracts/
      slices/
      orchestration/             # cross-domain workflow
    
    tooling/                     # DX 도구
      application/
    
    apps/                        # 애플리케이션 진입점
      runtimeapi/
      opscli/
```

### 도메인별 내부 구조

각 도메인은 헥사고날 구조:

```
<domain>/
  domain/                       # 순수 의미 모델 + 불변식 + 순수 계산
  application/                  # 유스케이스(오케스트레이션) + 입력 검증
  ports/                        # 외부 의존 계약(인터페이스)
  adapters/                     # 인프라 구현(DB, registry, messaging 등)
```

### shared 패키지 규칙

**shared가 포함할 것(허용)**:
- 결정성 유틸: canonical JSON, 정렬 규칙, hashing
- 공통 에러 모델: DomainError 계열
- 공통 타입: TenantId, EntityKey, Version, ContractRef, Hash
- 공통 포트: ClockPort, SingleFlightPort, ContractRegistryPort, ObservabilityPort, TenantValidatorPort
- 공통 wiring: 도메인 조립

**shared가 포함하면 안 되는 것(금지)**:
- RawData/ChangeSet/Slicing/View/Sink의 의미 로직
- 특정 도메인의 정책/룰/계산식
- "편하니까" 라는 이유의 공통화

### 도메인 간 의존성 규칙

1. **직접 참조(import) 금지**
   - `slicing.domain → rawdata.domain` import 금지
   - 도메인 간 데이터 접근/요청은 항상 ports 계약을 통해서만

2. **도메인 간 연결은 wiring에서만 허용**
   - `shared/wiring/*` 또는 `apps/*/wiring/*`에서만 조립 허용
   - 도메인 폴더 내부에서 타 도메인 adapter를 new/주입하는 코드 금지

3. **공유는 타입만**
   - 공통 타입은 shared/domain에만 존재
   - 도메인 의미 모델을 shared로 올리는 행위는 금지

4. **Contract Registry는 shared 포트 (P0)**
   - ContractRegistryPort는 모든 도메인에서 공통 사용
   - 각 도메인의 adapters/registry/ContractRegistryAdapter는 shared/ports.ContractRegistryPort 구현

### Gradle 멀티모듈 승격 경로

v0는 단일 모듈로 시작하되, 디렉터리 구조를 그대로 멀티모듈로 승격 가능하게 고정:

```
:shared
:rawdata
:slicing
:view
:changeset
:cdc
:sink
:ops
:apps:runtimeapi
:apps:cdcworker
:apps:sinkworker
:apps:opscli
```

## Consequences

### Positive

- ✅ 도메인 경계를 파일/패키지 레벨에서 즉시 보이게 고정
- ✅ 도메인 간 연결은 오직 wiring에서만 발생하도록 강제
- ✅ shared는 최소 공통 코어만 유지(비즈니스 의미 로직 금지)
- ✅ v0 단일 모듈로 시작하되, 동일 트리를 멀티모듈로 자연스럽게 승격 가능

### Negative

- ⚠️ 초기 구조 설계 비용
- ⚠️ 도메인 간 통신은 ports를 통해서만 가능하여 간접적
- ⚠️ wiring 복잡도 증가 가능

### Neutral

- 패키지 구조 복잡도
- 도메인 경계 유지 오버헤드

---

## 참고

- [RFC-V4-005](../rfc/rfc005.md) - 원본 RFC 문서
- [RFC-V4-001](../rfc/rfc001.md) - Contract-First 아키텍처
