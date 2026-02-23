# IVM-Lite 전체 아키텍처 흐름 다이어그램

**작성일**: 2026-02-12
**목적**: 중복 역할 확인 및 전체 데이터 흐름 가시화

---

## 1. 전체 시스템 아키텍처 (Bird's Eye View)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         외부 서비스 (External Services)                    │
│  - Product Sync Job (jobId: "product-sync-001")                         │
│  - Brand Update Job (jobId: "brand-update-002")                         │
│  - Category Batch (jobId: "category-batch-003")                         │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                                 │ 1. HTTP POST /api/v1/ingest (jobId 포함)
                                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    IVM-Lite Runtime API (:8080)                          │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ API Layer (apps/runtimeapi/)                                     │   │
│  │                                                                   │   │
│  │  IngestRoutes.kt                                                 │   │
│  │    ↓                                                              │   │
│  │  - Request 검증 (IngestRequest)                                   │   │
│  │  - DTO → Domain 변환 (TenantId, EntityKey, SemVer)              │   │
│  │  - IngestWorkflow 호출 (jobId 전달)                              │   │
│  │  - Result → IngestResponse 변환                                  │   │
│  │                                                                   │   │
│  │  JobStatusRoutes.kt (신규)                                        │   │
│  │    ↓                                                              │   │
│  │  - jobId 파라미터 검증                                            │   │
│  │  - OutboxRepository.findByJobId() 호출                           │   │
│  │  - Result → JobStatusResponse 변환                               │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                 │                                         │
│                                 │ 2. Engine Layer 호출                    │
│                                 ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ Engine Layer (pkg/)                                              │   │
│  │                                                                   │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │ IngestWorkflow (orchestration/application/)              │   │   │
│  │  │                                                           │   │   │
│  │  │  3. RawDataRecord 생성                                     │   │   │
│  │  │     - payload canonical JSON 변환                         │   │   │
│  │  │     - SHA256 hash 계산                                    │   │   │
│  │  │     - version 관리                                        │   │   │
│  │  │                                                           │   │   │
│  │  │  4. OutboxEntry 생성 (jobId 포함)                         │   │   │
│  │  │     - eventType: "RawDataIngested"                       │   │   │
│  │  │     - aggregateId: "tenantId:entityKey"                  │   │   │
│  │  │     - jobId: 외부 서비스 jobId                            │   │   │
│  │  │     - idempotencyKey: SHA256(aggregateId+eventType+payload)│   │
│  │  │                                                           │   │   │
│  │  │  5. UnitOfWork 트랜잭션 실행                               │   │   │
│  │  │     - RawData + Outbox 원자적 저장                        │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  │                                 │                                 │   │
│  │                                 ▼                                 │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │ Repository Layer (rawdata/adapters/)                     │   │   │
│  │  │                                                           │   │   │
│  │  │  JooqRawDataRepository                                    │   │   │
│  │  │    - putIdempotent(): raw_data 테이블 저장                │   │   │
│  │  │                                                           │   │   │
│  │  │  JooqOutboxRepository                                     │   │   │
│  │  │    - insert(): outbox 테이블 저장 (job_id 컬럼 포함)      │   │   │
│  │  │    - findByJobId(): jobId로 이벤트 조회                   │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ 6. PostgreSQL 저장
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          PostgreSQL Database                             │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ raw_data 테이블                                                   │   │
│  │  - tenant_id, entity_key, version (PK)                          │   │
│  │  - schema_id, schema_version                                    │   │
│  │  - payload (JSONB)                                              │   │
│  │  - payload_hash (SHA256)                                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ outbox 테이블                                                     │   │
│  │  - id (UUID, PK)                                                │   │
│  │  - job_id (VARCHAR(255), nullable) ★ 신규                       │   │
│  │  - idempotency_key (UNIQUE)                                     │   │
│  │  - aggregate_type (RAW_DATA, SLICE, VIEW)                      │   │
│  │  - aggregate_id (tenantId:entityKey)                           │   │
│  │  - event_type (RawDataIngested, ViewsComposed, ShipRequested)  │   │
│  │  - payload (JSONB)                                              │   │
│  │  - status (PENDING, PROCESSING, COMPLETED, FAILED)             │   │
│  │  - created_at, processed_at, retry_count                       │   │
│  │                                                                   │   │
│  │  인덱스:                                                          │   │
│  │    - idx_outbox_job_id (job_id) WHERE job_id IS NOT NULL       │   │
│  │    - idx_outbox_job_id_event_type (job_id, event_type)         │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ 7. Outbox Polling Worker (비동기)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    OutboxPollingWorker (Background)                      │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ RawDataIngestedHandler                                           │   │
│  │   ↓                                                               │   │
│  │   8. Outbox에서 RawDataIngested 이벤트 claim                      │   │
│  │      - status: PENDING → PROCESSING                              │   │
│  │   9. SlicingWorkflow 실행                                         │   │
│  │      - RuleSet 조회 및 실행                                       │   │
│  │      - Slice 생성 및 저장                                         │   │
│  │  10. SliceUpdated Outbox 생성 (jobId 전파)                       │   │
│  │      - eventType: "SliceUpdated"                                 │   │
│  │      - jobId: 상위 이벤트의 jobId                                 │   │
│  │  11. status: COMPLETED                                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ SliceUpdatedHandler                                              │   │
│  │   ↓                                                               │   │
│  │  12. SliceUpdated 이벤트 claim                                    │   │
│  │  13. ViewCompositionWorkflow 실행                                 │   │
│  │      - ViewDefinition 조회                                        │   │
│  │      - Slice 조합 (JOIN 등)                                       │   │
│  │      - View 저장                                                  │   │
│  │  14. ViewsComposed Outbox 생성 (jobId 전파)                      │   │
│  │      - eventType: "ViewsComposed"                                │   │
│  │      - jobId: 상위 이벤트의 jobId                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ ShipEventHandler (RFC Phase 1 구현 완료)                         │   │
│  │   ↓                                                               │   │
│  │  15. ViewsComposed 이벤트 claim                                   │   │
│  │  16. entityType 추출 (예: "product:123" → "product")             │   │
│  │  17. SinkRuleRegistry.findByEntityType()                         │   │
│  │      - PRODUCT → [OpenSearch, Personalize]                       │   │
│  │  18. 각 SinkRule에 대해 ShipRequested Outbox 생성 (jobId 전파)   │   │
│  │      - eventType: "ShipRequested"                                │   │
│  │      - payload: { sink: "OPENSEARCH", entityKey: "...", ... }   │   │
│  │      - jobId: 상위 이벤트의 jobId                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ ShipRequestedHandler                                             │   │
│  │   ↓                                                               │   │
│  │  19. ShipRequested 이벤트 claim                                   │   │
│  │  20. ShipWorkflow 실행                                            │   │
│  │      - Slice 조회                                                 │   │
│  │      - Sink 전송 (OpenSearch, Personalize 등)                    │   │
│  │  21. status: COMPLETED                                           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ 22. 외부 Sink 전송
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           External Sinks                                 │
│                                                                           │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌───────────────┐ │
│  │   OpenSearch         │  │   AWS Personalize    │  │  Custom Sink  │ │
│  │  (검색 엔진)          │  │  (추천 엔진)          │  │  (Webhook 등) │ │
│  └──────────────────────┘  └──────────────────────┘  └───────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. jobId 추적 흐름 (End-to-End)

```
외부 서비스: jobId = "product-sync-20260212-001"
    │
    │ POST /api/v1/ingest
    │ { jobId: "product-sync-20260212-001", ... }
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Outbox 테이블 (동일 jobId로 모든 이벤트 추적)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  [1] RawDataIngested                                             │
│      - job_id: "product-sync-20260212-001"                       │
│      - status: COMPLETED                                         │
│      - created_at: 2026-02-12T10:30:10Z                          │
│      - processed_at: 2026-02-12T10:30:12Z                        │
│                                                                   │
│  [2] SliceUpdated                                                │
│      - job_id: "product-sync-20260212-001"  ← 전파               │
│      - status: COMPLETED                                         │
│      - created_at: 2026-02-12T10:30:13Z                          │
│      - processed_at: 2026-02-12T10:30:15Z                        │
│                                                                   │
│  [3] ViewsComposed                                               │
│      - job_id: "product-sync-20260212-001"  ← 전파               │
│      - status: COMPLETED                                         │
│      - created_at: 2026-02-12T10:30:16Z                          │
│      - processed_at: 2026-02-12T10:30:18Z                        │
│                                                                   │
│  [4] ShipRequested (OpenSearch)                                  │
│      - job_id: "product-sync-20260212-001"  ← 전파               │
│      - status: PROCESSING                                        │
│      - created_at: 2026-02-12T10:30:19Z                          │
│                                                                   │
│  [5] ShipRequested (Personalize)                                 │
│      - job_id: "product-sync-20260212-001"  ← 전파               │
│      - status: PENDING                                           │
│      - created_at: 2026-02-12T10:30:19Z                          │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
    │
    │ GET /api/v1/jobs/product-sync-20260212-001/status
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ JobStatusResponse                                                │
│ {                                                                 │
│   "jobId": "product-sync-20260212-001",                          │
│   "eventCount": 5,                                               │
│   "events": [                                                     │
│     { "eventType": "RawDataIngested", "status": "COMPLETED" },   │
│     { "eventType": "SliceUpdated", "status": "COMPLETED" },      │
│     { "eventType": "ViewsComposed", "status": "COMPLETED" },     │
│     { "eventType": "ShipRequested", "status": "PROCESSING" },    │
│     { "eventType": "ShipRequested", "status": "PENDING" }        │
│   ]                                                               │
│ }                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 레이어별 역할 분석 (중복 체크)

### API Layer (apps/runtimeapi/)

| 컴포넌트 | 역할 | 중복 여부 |
|---------|------|----------|
| **IngestRoutes** | - HTTP 요청/응답 처리<br>- DTO 검증 (IngestRequest)<br>- DTO ↔ Domain 변환<br>- IngestWorkflow 호출 | ✅ 고유 (HTTP boundary) |
| **JobStatusRoutes** | - jobId 파라미터 검증<br>- OutboxRepository.findByJobId() 호출<br>- Result → JobStatusResponse 변환 | ✅ 고유 (HTTP boundary) |
| **QueryRoutes** | - View 조회 API<br>- ViewReader.getView() 호출 | ✅ 고유 (다른 usecase) |
| **OutboxRoutes** | - Outbox Admin API<br>- Outbox 수동 조작 | ✅ 고유 (Admin용) |

**결론**: API Layer는 순수 HTTP boundary 역할만 수행. Engine과 중복 없음.

---

### Engine Layer (pkg/)

#### Orchestration (orchestration/application/)

| 컴포넌트 | 역할 | 중복 여부 |
|---------|------|----------|
| **IngestWorkflow** | - RawDataRecord 생성<br>- OutboxEntry 생성 (RawDataIngested)<br>- UnitOfWork 트랜잭션 실행<br>- jobId 전달 | ✅ 고유 (진입점) |
| **SlicingWorkflow** | - RuleSet 조회 및 실행<br>- Slice 생성 및 저장<br>- OutboxEntry 생성 (SliceUpdated) | ✅ 고유 (RuleSet 적용) |
| **ViewCompositionWorkflow** | - ViewDefinition 조회<br>- Slice 조합 (JOIN)<br>- View 저장<br>- OutboxEntry 생성 (ViewsComposed) | ✅ 고유 (View 조합) |
| **ShipWorkflow** | - Slice 조회<br>- Sink 전송 (OpenSearch, Personalize) | ✅ 고유 (Sink 전송) |
| **OutboxPollingWorker** | - Outbox 폴링<br>- EventHandler 라우팅 | ✅ 고유 (Worker 관리) |

**결론**: 각 Workflow는 명확히 분리된 책임. 중복 없음.

---

#### Event Handlers (orchestration/application/)

| 컴포넌트 | 역할 | 중복 여부 |
|---------|------|----------|
| **RawDataIngestedHandler** | - RawDataIngested 이벤트 처리<br>- SlicingWorkflow 호출<br>- jobId 전파 | ✅ 고유 (Slicing 트리거) |
| **SliceUpdatedHandler** | - SliceUpdated 이벤트 처리<br>- ViewCompositionWorkflow 호출<br>- jobId 전파 | ✅ 고유 (View 조합 트리거) |
| **ShipEventHandler** | - ViewsComposed 이벤트 처리<br>- SinkRule 조회<br>- ShipRequested Outbox 생성<br>- jobId 전파 | ✅ 고유 (Ship 트리거) |
| **ShipRequestedHandler** | - ShipRequested 이벤트 처리<br>- ShipWorkflow 호출 | ✅ 고유 (Ship 실행) |

**결론**: Event Handler는 각 이벤트 타입별로 명확히 분리. 중복 없음.

---

#### Repository (rawdata/adapters/, slices/adapters/)

| 컴포넌트 | 역할 | 중복 여부 |
|---------|------|----------|
| **JooqRawDataRepository** | - raw_data 테이블 CRUD<br>- putIdempotent() | ✅ 고유 (RawData 전용) |
| **JooqOutboxRepository** | - outbox 테이블 CRUD<br>- insert(), findByJobId()<br>- claim(), markProcessed() | ✅ 고유 (Outbox 전용) |
| **JooqSliceRepository** | - slices 테이블 CRUD<br>- batchGet(), save() | ✅ 고유 (Slice 전용) |
| **JooqViewRepository** | - views 테이블 CRUD<br>- getView(), saveView() | ✅ 고유 (View 전용) |

**결론**: 각 Repository는 단일 테이블에만 책임. 중복 없음.

---

## 4. 중복 역할 분석 결과

### ✅ 중복 없음 (Clean Architecture 준수)

| 레이어 | 역할 | 책임 범위 |
|--------|------|----------|
| **API Layer** | HTTP boundary | - 요청/응답 검증<br>- DTO ↔ Domain 변환<br>- Engine 호출 |
| **Workflow Layer** | Business orchestration | - 도메인 로직 조율<br>- 여러 Repository 조합<br>- Outbox 이벤트 생성 |
| **Event Handler Layer** | Event-driven processing | - Outbox 이벤트 처리<br>- Workflow 트리거<br>- jobId 전파 |
| **Repository Layer** | Data persistence | - 단일 테이블 CRUD<br>- DB 트랜잭션 관리 |

### 🎯 설계 원칙 준수

1. **Single Responsibility**: 각 컴포넌트는 단일 책임
2. **Separation of Concerns**: API ↔ Engine ↔ Repository 명확히 분리
3. **No Duplication**: 동일 책임을 가진 컴포넌트 없음
4. **Dependency Direction**: API → Engine → Repository (단방향)

---

## 5. jobId 전파 체인 (중복 없이 전달)

```
IngestWorkflow (jobId 최초 수신)
    ↓
OutboxEntry.create(jobId = "product-sync-001")
    ↓
outbox 테이블 저장 (job_id = "product-sync-001")
    ↓
RawDataIngestedHandler.process()
    ↓ entry.jobId 읽기
SlicingWorkflow
    ↓
OutboxEntry.create(jobId = entry.jobId)  ← 전파
    ↓
SliceUpdatedHandler.process()
    ↓ entry.jobId 읽기
ViewCompositionWorkflow
    ↓
OutboxEntry.create(jobId = entry.jobId)  ← 전파
    ↓
ShipEventHandler.process()
    ↓ entry.jobId 읽기
ShipRequested Outbox 생성
    ↓
OutboxEntry.create(jobId = entry.jobId)  ← 전파
    ↓
ShipRequestedHandler.process()
    ↓
ShipWorkflow (jobId는 로깅/트레이싱용)
```

**전파 메커니즘**: 각 Handler가 처리한 OutboxEntry의 jobId를 다음 OutboxEntry에 그대로 복사.

---

## 6. 데이터베이스 테이블 관계

```
┌─────────────────────────────────────────────────────────────────┐
│ raw_data (원본 데이터)                                            │
│  - tenant_id + entity_key + version (PK)                        │
│  - payload (JSONB)                                              │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ 1:N (하나의 RawData → 여러 Slice)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ slices (파생 데이터 조각)                                         │
│  - tenant_id + entity_key + slice_type + version (PK)          │
│  - payload (JSONB)                                              │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ N:1 (여러 Slice → 하나의 View)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ views (조합된 뷰)                                                 │
│  - tenant_id + view_id + entity_key + version (PK)             │
│  - payload (JSONB)                                              │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ 1:N (하나의 View → 여러 Sink)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ sinks (외부 전송 기록)                                            │
│  - OpenSearch (검색 엔진)                                        │
│  - AWS Personalize (추천 엔진)                                   │
│  - Custom Webhook                                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ outbox (이벤트 저장소 - 모든 파이프라인 추적)                      │
│  - id (UUID, PK)                                                │
│  - job_id (VARCHAR, nullable) ★ 외부 서비스 jobId               │
│  - event_type (RawDataIngested, SliceUpdated, ViewsComposed...)│
│  - aggregate_id (tenantId:entityKey)                           │
│  - status (PENDING, PROCESSING, COMPLETED, FAILED)             │
│                                                                   │
│  역할:                                                            │
│    1. 이벤트 원자성 보장 (비즈니스 데이터와 같은 트랜잭션)          │
│    2. 비동기 처리 큐 (OutboxPollingWorker)                       │
│    3. jobId end-to-end 추적                                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 중복 제거 포인트 (이미 적용됨)

### ✅ 1. 단일 Outbox 테이블
- **AS-IS**: raw_data_outbox, slice_outbox, view_outbox 분리 (중복)
- **TO-BE**: 단일 outbox 테이블 (aggregate_type으로 구분)
- **이점**: 스키마 단순화, jobId 조회 단일 쿼리

### ✅ 2. UnitOfWork 패턴
- **AS-IS**: RawDataRepository + OutboxRepository 개별 호출 (원자성 미보장)
- **TO-BE**: IngestUnitOfWorkPort (단일 트랜잭션)
- **이점**: 원자성 보장, 롤백 간소화

### ✅ 3. EventHandler 통합
- **AS-IS**: 각 이벤트마다 별도 Worker (중복 폴링 로직)
- **TO-BE**: 단일 OutboxPollingWorker + EventHandler 라우팅
- **이점**: 폴링 로직 중복 제거, 확장 용이

### ✅ 4. SinkRule 기반 라우팅
- **AS-IS**: 각 Handler가 Sink 목록 하드코딩 (중복)
- **TO-BE**: SinkRuleRegistry (단일 SSOT)
- **이점**: Sink 추가/삭제 시 코드 변경 불필요

---

## 8. 최종 결론

### ✅ 중복 역할 없음
- API Layer: HTTP boundary만 담당
- Engine Layer: 비즈니스 로직만 담당
- Repository Layer: 데이터 영속성만 담당

### ✅ 명확한 책임 분리
- IngestWorkflow: RawData 저장 + Outbox 생성
- SlicingWorkflow: Slice 생성
- ViewCompositionWorkflow: View 조합
- ShipWorkflow: Sink 전송

### ✅ jobId 전파 체인 명확
- OutboxEntry.jobId로 모든 이벤트 추적
- Handler가 jobId를 다음 Outbox에 전달
- 단일 Outbox 테이블로 end-to-end 조회

### ✅ SOTA급 아키텍처
- Clean Architecture 준수
- Hexagonal Architecture 패턴
- Event-Driven Design
- Transactional Outbox Pattern
- Single Source of Truth (SinkRule, Contract)

---

**작성자**: Claude Sonnet 4.5
**검증**: Architecture Flow Analysis ✅
