# Admin Application (관리자 앱)

> **목적**: IVM Lite 시스템 모니터링 및 관리 전용 애플리케이션  
> **포트**: 8081 (기본값, `ADMIN_PORT` 환경 변수로 변경 가능)  
> **접속**: http://localhost:8081/

---

## 구조

```
apps/admin/
├── AdminApplication.kt      # 메인 애플리케이션
├── routes/
│   └── AdminRoutes.kt      # API 엔드포인트
└── dto/
    └── ApiError.kt         # 에러 DTO

resources/static/admin/
└── index.html              # 관리자 페이지 UI
```

---

## 실행 방법

### 1. Gradle Task로 실행

```bash
./gradlew runAdmin
```

### 2. 포트 변경

```bash
ADMIN_PORT=9000 ./gradlew runAdmin
```

### 3. 직접 실행

```bash
kotlin -cp build/libs/ivm-lite.jar com.oliveyoung.ivmlite.apps.admin.AdminApplicationKt
```

---

## API 엔드포인트

### GET /

관리자 페이지 UI (index.html)

### GET /dashboard

전체 대시보드 데이터

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
    "byStatus": {...},
    "byType": {...},
    "details": [...]
  },
  "worker": {
    "running": true,
    "processed": 1234,
    "failed": 0,
    "polls": 5678
  },
  "database": {
    "rawDataCount": 1000,
    "outboxCount": 1246
  },
  "timestamp": "2026-01-27T10:10:00Z"
}
```

### GET /outbox/stats

Outbox 통계만 조회

### GET /worker/status

Worker 상태 및 메트릭만 조회

### GET /db/stats

데이터베이스 통계만 조회

### GET /outbox/recent?limit=20

최근 처리된 Outbox 엔트리

### GET /outbox/failed?limit=20

실패한 Outbox 엔트리

---

## Runtime API와의 차이

| 항목 | Runtime API | Admin App |
|------|------------|-----------|
| **포트** | 8080 | 8081 |
| **목적** | 비즈니스 API (Ingest, Query) | 모니터링/관리 |
| **Worker** | 포함 (OutboxPollingWorker) | 포함 (동일한 Worker) |
| **Static Files** | 없음 | `/` → `static/admin/` |
| **API 경로** | `/api/v1/*` | `/dashboard`, `/outbox/*` 등 |

---

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `ADMIN_PORT` | 관리자 앱 포트 | `8081` |
| `DB_URL` | PostgreSQL 연결 URL | (필수) |
| `DYNAMODB_TABLE` | DynamoDB 테이블명 | (필수) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OpenTelemetry 엔드포인트 | `http://localhost:4317` |

---

## 사용 예시

### 브라우저 접속

```
http://localhost:8081/
```

### API 직접 호출

```bash
# 전체 대시보드
curl http://localhost:8081/dashboard

# Outbox 통계
curl http://localhost:8081/outbox/stats

# Worker 상태
curl http://localhost:8081/worker/status
```

---

## 보안 고려사항

⚠️ **현재는 인증/인가가 없습니다!**

Production 환경에서는 다음을 추가해야 합니다:

1. **인증**: Basic Auth, OAuth2, JWT 등
2. **인가**: 역할 기반 접근 제어 (RBAC)
3. **HTTPS**: TLS/SSL 암호화
4. **Rate Limiting**: API 호출 제한
5. **IP 화이트리스트**: 특정 IP만 접근 허용

---

## 관련 문서

- [관리자 대시보드 사용 가이드](./admin-dashboard.md)
- [Runtime API](./docs/rfc/rfcimpl009.md)
