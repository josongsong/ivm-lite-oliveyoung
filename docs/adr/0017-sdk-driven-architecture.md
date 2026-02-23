# ADR-0017: SDK-Driven Architecture — Outbox 제거

**Status**: Proposed  
**Date**: 2026-02-12  
**Deciders**: Architecture Team  
**RFC**: RFC-018 (sdk)

---

## Context

Outbox 패턴 기반 아키텍처는 운영 복잡도(폴링, Worker 모니터링, Stale Entry 복구)가 높았습니다. 사용자는 API만 호출하고 이후 흐름을 제어할 수 없었습니다.

## Decision

**SDK-Driven Architecture**로 전환합니다. Outbox 제거, SDK가 전체 흐름 제어.

### 핵심 변경

- Outbox 테이블 + Worker 제거
- 엔진은 Stateless API만 제공
- SDK가 RawData → Slicing → View → Sink 흐름 제어
- 재시도 정책을 SDK에서 제공 (사용자 제어 가능)

### 아키텍처

```
SDK.processEntity(rawData)
  → POST /slicing/trigger
  → POST /views/compose (Sink 발송 자동)
  → RetryPolicy (ExponentialBackoff)
```

Sink 발송은 엔진 책임 (ViewComposerWithSink + SinkDispatcher).

## Consequences

### Positive

- ✅ 운영 복잡도 감소 (Worker 제거)
- ✅ 폴링 오버헤드 제거
- ✅ 재시도 정책 커스터마이징 가능

### Negative

- ⚠️ SDK 전환 작업 필요
- ⚠️ 기존 Outbox 클라이언트 마이그레이션

---

## 참고

- [RFC-018 (sdk)](../rfc_archive/2026-02/RFC-018-sdk-driven-architecture.md)
- [ROADMAP](../rfc_archive/2026-02/ROADMAP.md)
