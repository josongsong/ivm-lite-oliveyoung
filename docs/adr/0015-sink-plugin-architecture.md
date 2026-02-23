# ADR-0015: Sink Plugin Architecture & Infrastructure as Code

**Status**: Accepted  
**Date**: 2026-02-12  
**Deciders**: Platform / Architecture Team  
**RFC**: RFC-017

---

## Context

Sink 로직이 엔진 코드와 혼재되어 있어, 새로운 Sink 타입(S3, Kinesis, OpenSearch 등) 추가 시 엔진 재배포가 필요했습니다.

## Decision

**Sink Plugin Architecture**를 채택합니다.

### 핵심 원칙

1. **엔진과 플러그인 완전 분리** — 의존성 방향: 플러그인 → 엔진 인터페이스만
2. **표준 계약 (SinkEnvelopeV1)** — 느슨한 결합
3. **독립 배포** — 엔진 재배포 없이 플러그인 추가/수정
4. **인프라 분리** — 로컬: Terraform, 운영: 인프라팀
5. **계약 버저닝** — Envelope 버전 관리로 호환성 보장

### 모듈 구조

- `sinks-contract/` — 독립 계약 모듈 (SinkPlugin, SinkEnvelopeV1, SinkError, SinkRoutingTable)
- `pkg/sinks/` — 엔진 (SinkDispatcher, SqsSinkPublisher)
- `plugins/sink-*/` — 플러그인 (S3, OpenSearch 등), Lambda로 독립 배포

### 데이터 흐름

```
View 생성 → SinkDispatcher → SQS 발행 → Lambda → SinkPlugin.execute()
```

## Consequences

### Positive

- ✅ 플러그인 독립 배포로 확장성 향상
- ✅ 계약 기반 느슨한 결합
- ✅ Gradle 멀티모듈로 명확한 경계

### Negative

- ⚠️ Lambda/인프라 운영 복잡도
- ⚠️ 플러그인 개발 오버헤드

---

## 참고

- [RFC-017](../rfc_archive/2026-02/RFC-017-sink-plugin-architecture.md)
- [ADR-0007](./0007-sink-orchestration.md)
