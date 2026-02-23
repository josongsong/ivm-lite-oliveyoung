# RFC — 구현 로드맵

**Last Updated**: 2026-02-12

---

## 전체 구조

```
RFC-017-SOTA (Sink Plugin Architecture)
    ↓
RFC-018 (SDK-Driven Architecture, Outbox 제거)
```

---

## 현재 상태

### ✅ 완료

| RFC | Phase | 완료일 | 핵심 결과물 |
|-----|-------|--------|-----------|
| **RFC-017-SOTA** | Phase 1 | 2026-02-12 | sinks-contract, SinkEnvelopeV1, S3 Plugin, Lambda, Terraform Preview |
| **RFC-017** | Full | 2026-02-12 | SOTA급 Sink Plugin Architecture 완전 구현 ✅ |

**RFC-017 완료 증거**:
- ✅ sinks-contract 모듈 (독립 계약)
- ✅ SinkEnvelopeV1 (버전별 계약)
- ✅ SinkDispatcher (엔진)
- ✅ SqsSinkPublisher (SQS 어댑터)
- ✅ S3SinkPlugin (18MB JAR)
- ✅ S3SinkLambdaHandler (Batch Failure)
- ✅ ViewComposerWithSink (자동 Sink 발송)
- ✅ Terraform Preview (9개 리소스)
- ✅ E2E 테스트 통과 (248ms Warm Start)

### ⏳ 진행 중

없음 (RFC-018 대기 중)

### 📋 계획

| RFC | Phase | 의존성 | 상태 |
|-----|-------|--------|------|
| **RFC-018** | Phase 1 | 없음 (RFC-017 완료) | **즉시 시작 가능** ✅ |
| **RFC-018** | Phase 2 | Phase 1 완료 | TBD |
| **RFC-018** | Phase 3 | Phase 2 완료 | TBD |

**변경사항**: RFC-017-SOTA Phase 2-3 제거 (Phase 1으로 충분)
- Ledger/Batch/DLQ는 나중에 필요 시 추가 (YAGNI 원칙)
- 현재 구현으로 SOTA급 달성 ✅

---

## RFC-017-SOTA: Sink Plugin Architecture

### Phase 1: 핵심 계약 ✅ (완료)

**목표**: SOTA급 Sink 계약 구조 구축

**완료 항목**:
- ✅ `sinks-contract` 모듈 (독립 계약 SSOT)
- ✅ `SinkEnvelopeV1` (메시지 계약)
- ✅ `SinkError` (3-tier 에러 분류)
- ✅ `SinkPlugin` 인터페이스
- ✅ `SinkJson` (JSON 정책 LOCK)
- ✅ `SinkRoutingTable`
- ✅ S3 Sink Plugin (18MB JAR)
- ✅ Lambda Handler (Batch Failure 지원)
- ✅ Terraform Preview 환경 (9개 리소스)
- ✅ E2E 테스트 (SQS → Lambda → S3)

**증거**:
- [sinks-contract/](../../sinks-contract/)
- [plugins/sink-s3/](../../plugins/sink-s3/)
- [infra/terraform/preview/](../../infra/terraform/preview/)

### Phase 2: SOTA 구현 ⏳ (계획)

**목표**: Ledger + Batch + DLQ 3-tier 구현

**TODO**:
- [ ] `SinkLedger` DynamoDB 구현
  - [ ] `tryStart()` (Optimistic Lock)
  - [ ] `complete()` / `fail()`
  - [ ] `queryForReplay()` (Replay/Backfill)
- [ ] `InMemorySinkLedger` (테스트용)
- [ ] S3 Sink Plugin 리팩토링
  - [ ] Ledger 통합
  - [ ] `executeBatch()` 구현
  - [ ] `PluginCapabilities` 선언
- [ ] Lambda Handler 개선
  - [ ] Partial Batch Response
  - [ ] Error Category 라우팅 (Retryable/NonRetryable/PoisonPill)
- [ ] Terraform DLQ 3-tier
  - [ ] Standard DLQ
  - [ ] Quarantine Queue
  - [ ] CloudWatch Alarm

**의존성**: RFC-017-SOTA Phase 1 완료 ✅

**예상 산출물**:
- `SinkLedgerPort.kt` + DynamoDB 어댑터
- `S3SinkPlugin` (Batch + Ledger)
- Terraform DLQ 리소스

### Phase 3: 운영 도구 📋 (계획)

**목표**: Replay/Backfill CLI + Grafana Dashboard

**TODO**:
- [ ] Replay CLI
  - [ ] DLQ → Main Queue 리드라이브
  - [ ] 필터링 (tenant, time, error code)
  - [ ] 속도 제어 (rate limit)
- [ ] Backfill CLI
  - [ ] 새 계약 버전 적용
  - [ ] Batch 처리
- [ ] Quarantine 분석 도구
- [ ] Grafana Dashboard (OTel)
  - [ ] Trace 시각화
  - [ ] Ledger 메트릭

**의존성**: RFC-017-SOTA Phase 2 완료 + RFC-018 Phase 2 완료 (Outbox 제거)

**예상 산출물**:
- `ivm-sink` CLI 도구
- Grafana JSON 템플릿

---

## RFC-018: SDK-Driven Architecture (Outbox 제거)

### Phase 1: 새 API + SDK ⏳ (계획)

**목표**: Outbox 없는 SDK 기반 플로우 구축

**TODO**:
- [ ] 새 API 구현
  - [ ] `POST /api/v1/slicing/trigger`
  - [ ] `POST /api/v1/views/compose`
    - [ ] ViewComposerWithSink 통합 (RFC-017-SOTA)
    - [ ] Sink 발송 자동화 (SinkDispatcher)
- [ ] SDK 구현 (`IvmLiteClient`)
  - [ ] `processEntity(rawData, viewDefId)` 메서드
  - [ ] `RetryPolicy` 인터페이스
  - [ ] `ExponentialBackoff` 구현
- [ ] SDK E2E 테스트
  - [ ] RawData → Slicing → View 플로우
  - [ ] Sink 발송 검증 (SQS 메시지)
- [ ] 기존 Outbox 방식과 결과 동일성 검증

**의존성**: **RFC-017-SOTA Phase 2 완료 필수** ⚠️

**이유**: RFC-018의 `POST /api/v1/views/compose`는 RFC-017-SOTA의 `SinkDispatcher`를 사용하므로, Ledger + Batch 처리가 먼저 완료되어야 합니다.

**예상 산출물**:
- `SlicingRoutes.kt` (새 API)
- `ViewRoutes.kt` (새 API)
- `IvmLiteClient.kt` (SDK)
- `RetryPolicy.kt`

### Phase 2: Outbox 제거 📋 (계획)

**목표**: Outbox 코드 완전 제거

**TODO**:
- [ ] Worker 비활성화 (`worker.enabled = false`)
- [ ] 모든 클라이언트 SDK 전환 확인
- [ ] Outbox 코드 제거
  - [ ] `OutboxEntry.kt`
  - [ ] `OutboxPollingWorker.kt`
  - [ ] `OutboxRepositoryPort.kt`
  - [ ] `OutboxRoutes.kt`
  - [ ] `OutboxHealthCheck.kt`
  - [ ] `OutboxStatus.kt`
- [ ] Outbox 테스트 제거 (~10개 파일)
- [ ] 메트릭 업데이트
  - [ ] Outbox 메트릭 제거
  - [ ] SDK 메트릭 추가
- [ ] 문서 업데이트

**의존성**: RFC-018 Phase 1 완료

**예상 산출물**:
- ~3000 LOC 제거
- 업데이트된 README/API 문서

### Phase 3: DB 정리 📋 (계획)

**목표**: Outbox 테이블 제거

**TODO**:
- [ ] Outbox 데이터 백업 (선택)
- [ ] `DROP TABLE outbox CASCADE`
- [ ] `DROP TABLE outbox_stats CASCADE`
- [ ] jOOQ 코드 재생성
- [ ] Flyway 마이그레이션 스크립트

**의존성**: RFC-018 Phase 2 완료 + 운영 환경 검증

**예상 산출물**:
- Flyway 마이그레이션 스크립트
- 백업 SQL 파일 (선택)

---

## 의존성 그래프

```
RFC-017 (✅ 완료)
    ↓
RFC-018 Phase 1 (⏳ 즉시 시작 가능)
    ↓
RFC-018 Phase 2 (📋 계획)
    ↓
RFC-018 Phase 3 (📋 계획)
```

**단순화**: RFC-017 완료로 RFC-018 즉시 시작 가능

---

## 크리티컬 패스

### 1. RFC-018 Phase 1 (즉시 시작) ✅

**RFC-017 완료로 모든 준비 완료**:
- ✅ ViewComposerWithSink 구현
- ✅ SinkDispatcher 동작
- ✅ SQS → Lambda → S3 검증

**TODO**:
- 새 API 구현 (`POST /slicing/trigger`, `POST /views/compose`)
- SDK 구현 (`IvmLiteClient`)
- E2E 테스트

### 2. RFC-018 Phase 2 (Outbox 제거)

SDK 안정화 후:
- Outbox Worker 비활성화
- Outbox 코드 제거
- 메트릭 업데이트

### 3. RFC-018 Phase 3 (DB 정리)

운영 검증 후:
- Outbox 테이블 DROP

---

## 리스크 및 완화 전략

| 리스크 | 영향도 | 완화 전략 |
|--------|--------|----------|
| **SDK 전환 실패** | 중간 | Phase 1에서 충분한 검증 + Canary 배포 |
| **Sink 발송 실패 증가** | 낮음 | SQS DLQ + Lambda 재시도 (RFC-017 완료) |
| **Outbox 제거 후 재시도 문제** | 낮음 | SDK RetryPolicy로 완화 |

**완화됨**: RFC-017 완료로 Sink 인프라 안정화 ✅

---

## 다음 단계

### ✅ RFC-017 완료!

**완료 내역**:
- sinks-contract 모듈 (계약 SSOT)
- SinkDispatcher + SqsSinkPublisher
- S3SinkPlugin + Lambda Handler
- ViewComposerWithSink (자동 Sink 발송)
- Terraform Preview 환경
- E2E 테스트 (248ms)

### 🎯 RFC-018 Phase 1 (즉시 시작 가능)

**이번 주 목표**:
1. [ ] 새 API 구현
   - [ ] `POST /api/v1/slicing/trigger`
   - [ ] `POST /api/v1/views/compose`
2. [ ] SDK `IvmLiteClient` 구현
   - [ ] `processEntity()` 메서드
   - [ ] `RetryPolicy` + `ExponentialBackoff`
3. [ ] SDK E2E 테스트

**2주 후 목표**:
1. [ ] RFC-018 Phase 2 (Outbox 제거)
   - [ ] Worker 비활성화
   - [ ] Outbox 코드 제거

**1개월 후 목표**:
1. [ ] RFC-018 Phase 3 (DB 정리)
   - [ ] Outbox 테이블 DROP

---

## 성공 지표

### RFC-017-SOTA

- [ ] Sink 발송 성공률 > 99.9%
- [ ] Lambda Cold Start < 500ms
- [ ] Ledger 조회 레이턴시 < 10ms
- [ ] DLQ 메시지 0건 (정상 상태)

### RFC-018

- [ ] API 응답 시간 < 2초 (RawData → Slicing → View 전체)
- [ ] SDK 재시도 성공률 > 95%
- [ ] Outbox Worker CPU 사용량 0% (제거 후)
- [ ] DB 부하 30% 감소 (Outbox 폴링 제거)

---

## 문서 링크

- [RFC-017-SOTA](./RFC-017-SOTA-IMPROVEMENTS.md)
- [RFC-018](./RFC-018-sdk-driven-architecture.md)
- [sinks-contract/compatibility-rules.md](../../sinks-contract/compatibility-rules.md)

---

**담당**: Platform Team
**검수**: Architecture Review Board
**승인**: CTO
