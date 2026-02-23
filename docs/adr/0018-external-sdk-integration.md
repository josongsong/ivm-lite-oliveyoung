# ADR-0018: External SDK Integration for Async Processing

**Status**: Proposed  
**Date**: 2026-02-12  
**Deciders**: Architecture Team  
**RFC**: RFC-019

---

## Context

외부 서비스가 OpenSearch/Personalize Sink로 직접 전송 시 복잡도가 증가하고, Ship 실패 시 재처리 로직을 각 서비스가 구현해야 했습니다.

## Decision

**External SDK Integration** 패턴을 채택합니다.

### 핵심 패턴

```
SDK → 동기 Slicing (RawData → Slices → Views) → 공용 Outbox 등록 → 비동기 Ship
```

- **SDK 제공**: REST API 래퍼 (Kotlin + TypeScript)
- **동기 처리**: RawData → Slicing → View (응답 타임아웃 내 완료)
- **비동기 처리**: ViewsComposed → SinkRule → ShipRequested → Ship
- **공용 Outbox**: 단일 `outbox` 테이블로 이벤트 관리
- **jobId 추적**: 외부 서비스의 jobId로 end-to-end 추적

### 데이터 흐름

1. 외부 서비스: `POST /api/v1/ingest` (jobId 포함)
2. SDK: 동기 Slicing → Outbox 등록 (ViewsComposed)
3. 응답: 200 OK (version, sliceCount)
4. Outbox Worker: consume → SinkOrchestrator → Ship

## Consequences

### Positive

- ✅ 외부 서비스는 API 호출만으로 Sink 전달
- ✅ jobId로 추적 가능
- ✅ Ship 실패 시 Outbox 재처리

### Negative

- ⚠️ Outbox 의존 (ADR-0017과 충돌 가능 — SDK-Driven 전환 시 재검토)

---

## 참고

- [RFC-019](../rfc_archive/2026-02/RFC-019-external-sdk-integration.md)
