RFC-IMPL-008 — Transactional Outbox Pattern (PostgreSQL + Polling/Debezium)

Status: Accepted
Created: 2026-01-25
Scope: 이벤트 발행의 원자성 보장 - "빵꾸 없이" 이벤트 발행
Depends on: RFC-IMPL-003, RFC-IMPL-004, RFC-IMPL-007
Audience: Platform / Infra / Runtime Developers
Non-Goals: 이벤트 소비자 구현, Saga 패턴, 이벤트 소싱

---

## Version Strategy

| Version | 방식 | 복잡도 | 용도 |
|---------|------|--------|------|
| **v1** | **Polling** | 낮음 | 개발/테스트/초기 운영 |
| v2 | Debezium + Kafka | 높음 | 대규모 운영 |

v1은 Polling 방식으로 시작하고, 트래픽 증가 시 v2로 전환.
**포트는 동일**하므로 어댑터만 교체하면 됨.

---

0. Executive Summary

본 RFC는 **Transactional Outbox 패턴**을 정의한다.

핵심 목표: **비즈니스 데이터 저장과 이벤트 발행을 원자적으로 처리** ("빵꾸 없이")

구현: **PostgreSQL (ACID 트랜잭션) + Debezium (CDC) → Kafka**

---

1. Problem Statement

### 1-1. 기존 문제 (Dual Write Problem)

```
[문제 시나리오]
1. App → DB에 데이터 저장 ✅
2. App → Kafka에 이벤트 발행 ❌ (네트워크 오류)
→ 결과: 데이터는 저장됐지만 이벤트는 유실 ("빵꾸")
```

### 1-2. 해결 방법

| 방법 | 원자성 | 복잡도 | SOTA |
|------|--------|--------|------|
| 2PC (분산 트랜잭션) | ✅ | ❌ 매우 높음 | ❌ |
| **Transactional Outbox** | ✅ | ✅ 낮음 | ✅ |
| Event Sourcing | ✅ | ⚠️ 패러다임 전환 | ⚠️ |

**Transactional Outbox = SOTA**

---

2. Architecture

### 2-1. 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application                              │
│                                                                  │
│   BEGIN TRANSACTION                                              │
│     ├─ INSERT INTO raw_data (...) VALUES (...)                   │
│     └─ INSERT INTO outbox (aggregatetype, aggregateid, payload)  │
│   COMMIT                                                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ WAL (Write-Ahead Log)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PostgreSQL                                  │
│   ┌────────────┐    ┌────────────┐                              │
│   │  raw_data  │    │   outbox   │  ← Debezium이 여기만 감시     │
│   └────────────┘    └────────────┘                              │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ CDC (Change Data Capture)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Debezium                                   │
│   PostgreSQL Connector + Outbox Event Router                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ Kafka Connect
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Kafka                                    │
│   ┌─────────────────────┐    ┌─────────────────────┐            │
│   │ ivm.events.raw_data │    │   ivm.events.slice  │            │
│   └─────────────────────┘    └─────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

### 2-2. 왜 PostgreSQL? (DynamoDB 아닌 이유)

| 항목 | PostgreSQL | DynamoDB |
|------|------------|----------|
| **트랜잭션** | ✅ 완전한 ACID | ⚠️ 제한적 (25개, 파티션) |
| **Cross-table 트랜잭션** | ✅ 자유로움 | ⚠️ 같은 파티션 권장 |
| **CDC** | ✅ Debezium (SOTA) | ⚠️ DynamoDB Streams |
| **Outbox 패턴 지원** | ✅ 업계 표준 | ⚠️ 복잡함 |

**결론**: Outbox가 필요한 비즈니스 데이터는 PostgreSQL, 조회 위주는 DynamoDB

---

## 2.5 v1 Architecture (Polling 방식)

v1에서는 Kafka/Debezium 없이 **Polling** 방식으로 시작:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application                              │
│                                                                  │
│   BEGIN TRANSACTION                                              │
│     ├─ INSERT INTO raw_data (...) VALUES (...)                   │
│     └─ INSERT INTO outbox (status='PENDING', ...)                │
│   COMMIT                                                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PostgreSQL                                  │
│   ┌────────────┐    ┌────────────┐                              │
│   │  raw_data  │    │   outbox   │  ← Polling Worker가 조회     │
│   └────────────┘    └────────────┘                              │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ Polling (Coroutine, 1초 간격)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Polling Worker                                 │
│   1. SELECT * FROM outbox WHERE status='PENDING' LIMIT 100      │
│   2. 후속 처리 (Slicing 트리거 등)                               │
│   3. UPDATE outbox SET status='PROCESSED' WHERE id IN (...)     │
└─────────────────────────────────────────────────────────────────┘
```

### v1 Outbox Table (Polling용 확장)

```sql
CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    aggregatetype   VARCHAR(128) NOT NULL,
    aggregateid     VARCHAR(256) NOT NULL,
    type            VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    
    -- v1 Polling용 필드
    status          VARCHAR(32) DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED
    processed_at    TIMESTAMPTZ,
    retry_count     INT DEFAULT 0,
    
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_outbox_status ON outbox(status, created_at);
```

### v1 OutboxRepositoryPort

```kotlin
interface OutboxRepositoryPort {
    suspend fun insert(entry: OutboxEntry): Result<Unit>
    suspend fun findPending(limit: Int): Result<List<OutboxEntry>>
    suspend fun markProcessed(ids: List<UUID>): Result<Unit>
    suspend fun markFailed(id: UUID, reason: String): Result<Unit>
}

data class OutboxEntry(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxStatus,
    val createdAt: Instant,
    val processedAt: Instant?,
    val retryCount: Int,
)

enum class OutboxStatus { PENDING, PROCESSED, FAILED }
```

---

3. Outbox Table Schema (v2 Debezium용)

```sql
CREATE TABLE outbox (
    id              UUID PRIMARY KEY,
    
    -- Debezium Outbox SMT 표준 필드
    aggregatetype   VARCHAR(128) NOT NULL,  -- 라우팅 키: "raw_data", "slice"
    aggregateid     VARCHAR(256) NOT NULL,  -- 파티션 키: "tenant:entity"
    type            VARCHAR(128) NOT NULL,  -- 이벤트 타입: "RawDataIngested"
    payload         JSONB NOT NULL,         -- 이벤트 페이로드
    
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### 3-1. 필드 설명

| 필드 | 역할 | Kafka 매핑 |
|------|------|------------|
| `aggregatetype` | 토픽 라우팅 | → `ivm.events.{aggregatetype}` |
| `aggregateid` | 메시지 키 | → Kafka message key (순서 보장) |
| `type` | 이벤트 타입 | → Kafka header `eventType` |
| `payload` | 이벤트 본문 | → Kafka message value |

---

4. Application Code Pattern

### 4-1. Kotlin Pseudo-code

```kotlin
class IngestWorkflow(
    private val rawDataRepo: RawDataRepositoryPort,
    private val outboxRepo: OutboxRepositoryPort,
    private val txManager: TransactionManager,
) {
    suspend fun execute(request: IngestRequest): Result<RawDataRecord> {
        return txManager.transaction {
            // 1. 비즈니스 데이터 저장
            val record = rawDataRepo.putIdempotent(/* ... */)
            
            // 2. Outbox 이벤트 저장 (같은 트랜잭션!)
            outboxRepo.insert(
                OutboxEvent(
                    aggregateType = "raw_data",
                    aggregateId = "${request.tenantId}:${request.entityKey}",
                    type = "RawDataIngested",
                    payload = buildPayload(record)
                )
            )
            
            record
        }
        // COMMIT 후 Debezium이 자동으로 Kafka에 발행
    }
}
```

### 4-2. OutboxRepositoryPort

```kotlin
interface OutboxRepositoryPort {
    suspend fun insert(event: OutboxEvent)
}

data class OutboxEvent(
    val aggregateType: String,
    val aggregateId: String,
    val type: String,
    val payload: Map<String, Any>,
)
```

---

5. Debezium Configuration

### 5-1. Outbox Event Router

Debezium의 **Outbox Event Router SMT**(Single Message Transform)가 핵심:

```json
{
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.route.topic.replacement": "ivm.events.${routedByValue}",
  "transforms.outbox.route.by.field": "aggregatetype",
  "transforms.outbox.table.field.event.key": "aggregateid",
  "transforms.outbox.table.field.event.payload": "payload"
}
```

### 5-2. 결과 토픽

| aggregatetype | Kafka Topic |
|---------------|-------------|
| `raw_data` | `ivm.events.raw_data` |
| `slice` | `ivm.events.slice` |
| `changeset` | `ivm.events.changeset` |

---

6. Event Schema (Kafka Message)

### 6-1. RawDataIngested

```json
{
  "eventId": "uuid",
  "eventType": "RawDataIngested",
  "timestamp": "2026-01-25T12:00:00Z",
  "aggregateId": "tenant-1:product-123",
  "payload": {
    "tenantId": "tenant-1",
    "entityKey": "product-123",
    "version": 1,
    "contentHash": "sha256:...",
    "schemaId": "product.v1",
    "schemaVersion": "1.0.0"
  }
}
```

### 6-2. SliceCreated

```json
{
  "eventId": "uuid",
  "eventType": "SliceCreated",
  "timestamp": "2026-01-25T12:00:01Z",
  "aggregateId": "tenant-1:product-123",
  "payload": {
    "tenantId": "tenant-1",
    "entityKey": "product-123",
    "sliceType": "CORE",
    "sliceVersion": 1,
    "contentHash": "sha256:..."
  }
}
```

---

7. Delivery Guarantees

### 7-1. At-Least-Once

- Debezium은 **at-least-once** 보장
- 장애 복구 시 중복 발행 가능
- **소비자가 멱등 처리 필수**

### 7-2. Exactly-Once 효과 달성

```
At-Least-Once (Debezium) + 멱등 소비자 = Exactly-Once 효과
```

소비자 멱등 처리 방법:
- `eventId` 기반 중복 체크
- `(aggregateId, version)` 기반 idempotency

---

8. Acceptance Criteria

### 8-1. 인프라
- [ ] `docker-compose up`으로 전체 스택 실행
- [ ] PostgreSQL WAL level = logical
- [ ] Debezium Connector 정상 등록

### 8-2. Outbox
- [ ] `OutboxRepositoryPort` 구현
- [ ] 비즈니스 저장 + Outbox INSERT가 하나의 트랜잭션

### 8-3. CDC
- [ ] outbox INSERT 후 Kafka 토픽에 이벤트 발행 확인
- [ ] `aggregateid`가 Kafka message key로 매핑 (순서 보장)

### 8-4. 장애 시나리오
- [ ] App 장애 시 트랜잭션 롤백 → 이벤트 유실 없음
- [ ] Debezium 장애 후 복구 시 → 이벤트 재발행 (at-least-once)

---

9. Local Development

```bash
# 전체 인프라 시작
./infra/setup-local.sh

# Kafka UI로 이벤트 확인
open http://localhost:8080

# PostgreSQL 접속
psql -h localhost -U ivm -d ivmlite

# Debezium 상태 확인
curl http://localhost:8083/connectors/ivm-outbox-connector/status | jq
```

---

10. Summary

| 항목 | 결정 |
|------|------|
| **패턴** | Transactional Outbox |
| **DB** | PostgreSQL (ACID) |
| **CDC** | Debezium |
| **메시징** | Kafka |
| **보장** | At-Least-Once + 멱등 = Exactly-Once 효과 |

**한 줄 요약**: 같은 트랜잭션에 저장 → Debezium이 알아서 발행 → 빵꾸 없음!
