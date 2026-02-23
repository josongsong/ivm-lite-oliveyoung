# ADR-0020: SOTA DSL DX — Developer Experience

**Status**: Proposed  
**Date**: 2026-02  
**Deciders**: Architecture Team  
**RFC**: RFC-021

---

## Context

API 사용 시 의도가 한 줄에 드러나지 않고, IDE 자동완성과 컴파일 타임 오류 검출이 부족했습니다.

## Decision

**SOTA급 DSL DX** 원칙을 채택합니다.

### 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **문장처럼 읽힌다** | `product("SKU-001") named "비타민C" priced 15_000` |
| **프로퍼티 = 할당** | `tenantId = "x"` (함수 호출 아님) |
| **타입이 보호한다** | `Views.Product.Pdp` → `ProductPdpData` 반환 |
| **실패는 명시적** | `getOrThrow()` / `onFailure { }` |
| **짧은 경로** | `Ivm.query()` not `Ivm.client().query()` |

### Deploy DSL

```kotlin
Ivm.product {
    tenantId = "oliveyoung"
    sku = "SKU-001"
    name = "비타민C"
    price = 15_000
}.deploy().onSuccess { println("v${it.version}") }
```

### Query DSL

- `Views.Product.Pdp` 등 타입 기반 접근
- `getOrThrow()` / `onFailure { }` 명시적 에러 처리

## Consequences

### Positive

- ✅ 90% 타이핑 없이 IDE 자동완성
- ✅ 컴파일 타임 오류 검출
- ✅ 읽기 쉬운 API

### Negative

- ⚠️ 기존 API 마이그레이션
- ⚠️ DSL 설계/구현 비용

---

## 참고

- [RFC-021](../rfc_archive/2026-02/RFC-021-sota-dsl-dx.md)
