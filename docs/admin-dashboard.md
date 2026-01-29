# 관리자 대시보드

> **목적**: IVM Lite 시스템 전반의 상태를 실시간으로 모니터링  
> **접속**: http://localhost:8080/admin/

---

## 기능

### 1. 실시간 대시보드

- **Outbox 통계**: 상태별(PENDING, PROCESSING, FAILED, PROCESSED) 개수
- **Worker 상태**: 실행 여부, 처리량, 실패 횟수
- **데이터베이스 통계**: RawData, Outbox 총 개수
- **자동 새로고침**: 30초마다 자동 업데이트

### 2. 상세 통계

- **Outbox 상태별 상세**: AggregateType별 통계, 평균 지연시간
- **최근 처리된 작업**: 최근 20개 Outbox 엔트리
- **실패한 작업**: 실패한 Outbox 엔트리 및 실패 사유

---

## API 엔드포인트

### GET /admin/dashboard

전체 대시보드 데이터를 한 번에 조회합니다.

**응답 예시**:
```json
{
  "outbox": {
    "total": {
      "pending": 10,
      "processing": 2,
      "failed": 0,
      "processed": 1234
    },
    "byStatus": {
      "PENDING": 10,
      "PROCESSING": 2,
      "PROCESSED": 1234
    },
    "byType": {
      "RAW_DATA": 5,
      "SLICE": 7
    },
    "details": [
      {
        "status": "PENDING",
        "aggregateType": "RAW_DATA",
        "count": 5,
        "oldest": "2026-01-27T10:00:00Z",
        "newest": "2026-01-27T10:05:00Z",
        "avgLatencySeconds": 2.5
      }
    ]
  },
  "worker": {
    "running": true,
    "processed": 1234,
    "failed": 0,
    "polls": 5678,
    "lastPollTime": null
  },
  "database": {
    "rawDataCount": 1000,
    "outboxCount": 1246,
    "note": "DynamoDB stats require separate query"
  },
  "timestamp": "2026-01-27T10:10:00Z"
}
```

---

### GET /admin/outbox/stats

Outbox 통계만 조회합니다.

**응답**: `OutboxStatsResponse`

---

### GET /admin/worker/status

Worker 상태 및 메트릭만 조회합니다.

**응답**: `WorkerStatusResponse`

---

### GET /admin/db/stats

데이터베이스 통계만 조회합니다.

**응답**: `DatabaseStatsResponse`

---

### GET /admin/outbox/recent?limit=20

최근 처리된 Outbox 엔트리를 조회합니다.

**Query Parameters**:
- `limit`: 최대 반환 개수 (기본값: 50)

**응답 예시**:
```json
{
  "items": [
    {
      "id": "550e8400-...",
      "aggregateType": "RAW_DATA",
      "aggregateId": "oliveyoung:PRODUCT:SKU-001",
      "eventType": "RawDataIngested",
      "status": "PROCESSED",
      "createdAt": "2026-01-27T10:00:00Z",
      "processedAt": "2026-01-27T10:00:02Z",
      "retryCount": 0
    }
  ],
  "count": 20
}
```

---

### GET /admin/outbox/failed?limit=20

실패한 Outbox 엔트리를 조회합니다.

**Query Parameters**:
- `limit`: 최대 반환 개수 (기본값: 20)

**응답 예시**:
```json
{
  "items": [
    {
      "id": "660e8400-...",
      "aggregateType": "SLICE",
      "aggregateId": "oliveyoung:PRODUCT:SKU-001",
      "eventType": "ShipRequested",
      "createdAt": "2026-01-27T10:00:00Z",
      "retryCount": 5,
      "failureReason": "OpenSearch connection timeout"
    }
  ],
  "count": 2
}
```

---

## 사용 방법

### 1. 브라우저에서 접속

```
http://localhost:8080/admin/
```

### 2. API 직접 호출

```bash
# 전체 대시보드
curl http://localhost:8080/admin/dashboard

# Outbox 통계만
curl http://localhost:8080/admin/outbox/stats

# Worker 상태만
curl http://localhost:8080/admin/worker/status
```

---

## 화면 구성

### 상단 헤더
- Worker 실행 상태 (실시간 표시)
- 새로고침 버튼
- 마지막 업데이트 시간

### 통계 카드 (3개)
1. **Outbox 전체 통계**: PENDING, PROCESSING, FAILED, PROCESSED 개수
2. **Worker 상태**: 실행 여부, 처리 완료, 처리 실패, Poll 횟수
3. **데이터베이스**: RawData, Outbox 총 개수

### 테이블 (3개)
1. **Outbox 상태별 상세 통계**: 상태 + AggregateType별 상세 정보
2. **최근 처리된 작업**: 최근 20개 Outbox 엔트리
3. **실패한 작업**: 실패한 Outbox 엔트리 및 실패 사유

---

## 자동 새로고침

- **주기**: 30초마다 자동 업데이트
- **범위**: 전체 대시보드 데이터, 최근 작업, 실패한 작업

---

## 문제 해결

### 페이지가 로드되지 않는 경우

1. **Static Resources 확인**:
   ```bash
   ls -la src/main/resources/static/admin/
   ```

2. **서버 로그 확인**:
   ```bash
   # 404 에러 확인
   grep "404" logs/application.log
   ```

### API가 응답하지 않는 경우

1. **데이터베이스 연결 확인**:
   ```bash
   curl http://localhost:8080/ready
   ```

2. **Worker 상태 확인**:
   ```bash
   curl http://localhost:8080/admin/worker/status
   ```

---

## 보안 고려사항

⚠️ **현재는 인증/인가가 없습니다!**

Production 환경에서는 다음을 추가해야 합니다:

1. **인증**: Basic Auth, OAuth2, JWT 등
2. **인가**: 역할 기반 접근 제어 (RBAC)
3. **HTTPS**: TLS/SSL 암호화
4. **Rate Limiting**: API 호출 제한

---

## 향후 개선 사항

- [ ] 실시간 업데이트 (WebSocket/SSE)
- [ ] 그래프/차트 추가 (Chart.js, D3.js)
- [ ] 필터링 및 검색 기능
- [ ] 알림 기능 (실패 작업 알림)
- [ ] 히스토리 보기 (과거 통계)
- [ ] Export 기능 (CSV, JSON)
