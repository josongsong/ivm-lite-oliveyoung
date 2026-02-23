# Sink 버퍼링 전략 — 실시간 경로

> 실시간 Sink 처리 시 "바로 처리" 대신 일정 시간/개수만큼 모아서 벌크 처리하는 SOTA 방식 제안.

---

## 1. 현재 vs 목표

| 구분 | 현재 | 목표 |
|------|------|------|
| **트리거** | SinkEvent 1건 → 즉시 Lambda | N건 또는 T초 모아서 → Lambda |
| **OpenSearch 요청** | 1건당 1회 | 배치당 1회 (bulk) |
| **20만 건 시** | 20만 번 HTTP | ~400번 (500개씩) |

---

## 2. SOTA 옵션 비교

### 옵션 A: SQS + Lambda Batch Window (권장)

```
IngestionWorkflow → SinkEvent 생성 → SQS sendMessageBatch
SQS 큐: ivm-sink-events
Lambda: SQS 이벤트 소스
  - batchSize: 500
  - batchWindow: 60초 (MaximumBatchingWindowInSeconds)
  - "500개 도달 OR 60초 경과" 시 Lambda 호출
Lambda: 500개 메시지 → SinkPayload 500개 → executeBatch(500)
```

| 항목 | 값 |
|------|-----|
| **버퍼 시간** | 최대 60초 (1~300초 설정 가능) |
| **버퍼 크기** | 최대 500건 (1~10,000 설정 가능) |
| **트리거** | 둘 중 먼저 도달 시 |
| **DynamoDB** | SinkEvent 테이블 제거 또는 audit 전용으로 분리 |

**장점**: AWS 네이티브, 설정만으로 시간+개수 하이브리드 버퍼링, 추가 인프라 없음.

---

### 옵션 B: DynamoDB Streams 유지 + SinkStreamProcessor 배치화

```
현재 구조 유지: SinkEvent → DynamoDB → Streams → Lambda
변경: SinkStreamProcessor가 수신 배치 전체를 executeBatch(전체)로 한 번에 호출
```

| 항목 | 값 |
|------|-----|
| **버퍼** | Streams가 넘겨주는 배치 (수십~수백 건) |
| **버퍼 시간** | 없음 (Streams 배치 도착 시점) |
| **변경 범위** | SinkStreamProcessor 내부만 |

**장점**: 아키텍처 변경 없음, 구현 단순.  
**단점**: Streams 배치 크기에 의존, "60초 모으기" 같은 시간 기반 버퍼링 불가.

---

### 옵션 C: Kinesis Data Streams

```
IngestionWorkflow → Kinesis PutRecord
Kinesis → Lambda (batchSize=500)
```

**장점**: 스트리밍 표준, 세밀한 제어.  
**단점**: Kinesis 도입 비용, DynamoDB Streams 대비 복잡도 증가.

---

### 옵션 D: SQS만 (Batch Window, DynamoDB 제거)

```
IngestionWorkflow → SinkEvent → SQS (DynamoDB 미사용)
SQS → Lambda (batchSize=500, batchWindow=60)
```

**장점**: 구조 단순, DynamoDB 비용 감소.  
**단점**: SinkEvent 이력/상태 조회 불가 (별도 저장소 필요 시 추가 설계).

---

## 3. 권장: 옵션 A (SQS + Batch Window)

### 3-1. 흐름

```
[IngestionWorkflow]
  → SinkEvent.create() (in-memory)
  → SQS.sendMessageBatch(25개씩)  // SQS 최대 10개/요청, sendMessageBatch는 10개
  → (선택) DynamoDB에도 저장 (audit/상태 조회용)

[SQS 큐: ivm-sink-events]
  - batchSize: 500
  - batchWindow: 60초
  - 메시지 축적

[Lambda: SinkBatchHandler]
  - 500개 또는 60초 후 호출
  - 500개 메시지 → 500개 SinkPayload
  - target별 그룹핑 (opensearch, s3 등)
  - plugin.executeBatch(500) per target
```

### 3-2. 설정 예시

| 파라미터 | 값 | 비고 |
|----------|-----|------|
| SQS batchSize | 500 | OpenSearch bulk 권장 크기 |
| batchWindow | 60 | 1분 대기 (트래픽 낮을 때) |
| visibilityTimeout | 300 | 5분 (처리 시간 여유) |
| DLQ | 활성화 | 실패 메시지 보존 |

### 3-3. SinkEvent → SQS 전환 시

| 기존 | 변경 |
|------|------|
| SinkEventRepository.putAll(DynamoDB) | SqsSinkEventPublisher.sendBatch(SQS) |
| DynamoDB Streams 트리거 | SQS 이벤트 소스 |
| SinkStreamHandler | SinkBatchHandler (새 Lambda) |

### 3-4. 하이브리드 (DynamoDB + SQS)

- **DynamoDB**: audit, Admin 대시보드용 (상태/이력)
- **SQS**: 실제 처리 경로
- IngestionWorkflow에서 SinkEvent를 DynamoDB와 SQS에 동시 전송

---

## 4. 구현 단계

### Phase 1: SinkStreamProcessor 배치화 (단기, 옵션 B)

- Streams 구조 유지
- 수신 배치 전체를 `executeBatch`로 처리
- 변경: `processBatch` 내부 로직만

### Phase 2: SQS + Batch Window 전환 (중기, 옵션 A)

- SqsSinkEventPublisher 추가
- SinkBatchHandler Lambda 추가
- IngestionWorkflow에서 SQS 발행으로 전환
- DynamoDB Streams 기반 Sink 경로 제거 또는 audit 전용으로 축소

---

## 5. 참고

- [AWS Lambda SQS Batch Window](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html) - MaximumBatchingWindowInSeconds (0~300초)
- batchSize 10 초과 시 batchWindow 최소 1초 필요
- SQS batchWindow + batchSize = "N건 도달 또는 T초 경과 시" 호출
