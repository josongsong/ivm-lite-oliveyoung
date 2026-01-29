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
│   ├── AdminRoutes.kt      # 대시보드, Outbox, Worker API
│   ├── ContractRoutes.kt   # Contract 관리 API
│   ├── PipelineRoutes.kt  # 파이프라인 모니터링 API
│   ├── AlertRoutes.kt     # 알림 관리 API
│   ├── BackfillRoutes.kt  # 백필 작업 API
│   ├── HealthRoutes.kt    # 헬스체크 API
│   ├── ObservabilityRoutes.kt # 모니터링 API
│   └── WorkflowCanvasRoutes.kt # 워크플로우 캔버스 API
├── dto/
│   └── ApiError.kt         # 에러 DTO
└── wiring/
    └── AdminModule.kt      # DI 모듈 설정

resources/static/admin/
├── index.html              # React Admin UI (SPA)
└── assets/                 # 정적 리소스
```

## 아키텍처

### 런타임 독립성
- `runtimeapi`와 완전히 독립적으로 실행 가능
- 별도 포트 (8081), 별도 프로세스
- 같은 데이터베이스 공유 (모니터링 목적)

### 코드 공유
- `wiring` 모듈은 `runtimeapi`와 공유 (코드 중복 방지)
- 실제 런타임은 완전히 독립적

### 주요 구성 요소
- **Koin DI**: Admin 앱 전용 모듈 (`adminAllModules`)
- **Content Negotiation**: JSON 직렬화
- **Status Pages**: 전역 에러 핸들링
- **Alert Engine**: 백그라운드에서 자동 시작
- **SPA Fallback**: 모든 경로를 `index.html`로 라우팅

---

## 실행 방법

### 1. 빠른 실행 (권장)

```bash
./gradlew fastAdmin
```

빠른 빌드 후 즉시 실행 (테스트 스킵)

### 2. 일반 실행

```bash
./gradlew runAdmin
```

전체 빌드 후 실행

### 3. 포트 변경

```bash
ADMIN_PORT=9000 ./gradlew fastAdmin
```

환경 변수로 포트 지정 (기본값: 8081)

### 4. Frontend 개발 서버 (별도 터미널)

```bash
cd admin-ui && npm run dev
```

React 개발 서버 실행 (포트 3000)

### 5. 직접 실행

```bash
kotlin -cp build/libs/ivm-lite.jar com.oliveyoung.ivmlite.apps.admin.AdminApplicationKt
```

JAR 파일로 직접 실행

---

## API 엔드포인트

**모든 API는 `/api` prefix를 사용합니다.**

### 1. 대시보드 & Outbox (`/api/dashboard`, `/api/outbox/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/dashboard` | 전체 대시보드 데이터 (Outbox, Worker, DB 통계) |
| GET | `/api/outbox/stats` | Outbox 통계만 조회 |
| GET | `/api/worker/status` | Worker 상태 및 메트릭 |
| GET | `/api/db/stats` | 데이터베이스 통계 |
| GET | `/api/outbox/recent?limit=20` | 최근 처리된 작업 |
| GET | `/api/outbox/failed?limit=20` | 실패한 작업 |
| GET | `/api/outbox/{id}` | 특정 작업 상세 |
| GET | `/api/outbox/dlq` | DLQ (Dead Letter Queue) 목록 |
| POST | `/api/outbox/dlq/{id}/replay` | DLQ 재처리 |
| POST | `/api/outbox/{id}/retry` | 작업 재시도 |
| POST | `/api/outbox/failed/retry-all` | 실패한 작업 전체 재시도 |
| GET | `/api/outbox/stats/hourly` | 시간대별 통계 |
| GET | `/api/outbox/stale` | Stale 작업 목록 |
| POST | `/api/outbox/stale/release` | Stale 해제 |

**응답 예시** (`/api/dashboard`):
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

### 2. 계약 관리 (`/api/contracts/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/contracts` | 전체 Contract 목록 |
| GET | `/api/contracts/schemas` | Entity Schema만 |
| GET | `/api/contracts/rulesets` | RuleSet만 |
| GET | `/api/contracts/views` | ViewDefinition만 |
| GET | `/api/contracts/sinks` | SinkRule만 |
| GET | `/api/contracts/{kind}/{id}` | 특정 Contract 상세 조회 |
| GET | `/api/contracts/stats` | Contract 통계 |

**참고**: 현재는 **Read-only** (조회만 가능, 생성/수정/삭제 미구현)

### 3. 파이프라인 모니터링 (`/api/pipeline/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/pipeline/overview` | 파이프라인 전체 상태 (Raw → Slice → View → Sink) |
| GET | `/api/pipeline/rawdata` | RawData 통계 |
| GET | `/api/pipeline/slices` | Slice 통계 |
| GET | `/api/pipeline/flow/{entityKey}` | 특정 엔티티의 데이터 흐름 상세 |
| GET | `/api/pipeline/recent` | 최근 처리된 파이프라인 |
| GET | `/api/pipeline/indexes` | 인덱스 목록 |

### 4. 알림 관리 (`/api/alerts/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/alerts` | 전체 알림 목록 |
| GET | `/api/alerts/active` | 활성 알림만 |
| GET | `/api/alerts/{id}` | 알림 상세 |
| POST | `/api/alerts/{id}/acknowledge` | 알림 확인 |
| POST | `/api/alerts/{id}/silence` | 알림 무시 |
| GET | `/api/alerts/rules` | 알림 규칙 목록 |
| GET | `/api/alerts/stats` | 알림 통계 |
| POST | `/api/alerts/evaluate` | 규칙 평가 (테스트용) |

### 5. 백필 작업 (`/api/backfill/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/backfill` | 백필 작업 목록 |
| POST | `/api/backfill` | 새 백필 작업 생성 |
| GET | `/api/backfill/active` | 활성 작업만 |
| GET | `/api/backfill/{id}` | 작업 상세 |
| POST | `/api/backfill/{id}/dry-run` | Dry-run 실행 |
| POST | `/api/backfill/{id}/start` | 작업 시작 |
| POST | `/api/backfill/{id}/pause` | 작업 일시정지 |
| POST | `/api/backfill/{id}/resume` | 작업 재개 |
| POST | `/api/backfill/{id}/cancel` | 작업 취소 |
| POST | `/api/backfill/{id}/retry` | 작업 재시도 |
| GET | `/api/backfill/stats` | 백필 통계 |

### 6. 헬스체크 (`/api/health/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/health` | 전체 시스템 상태 |
| GET | `/api/health/live` | Liveness probe |
| GET | `/api/health/ready` | Readiness probe |
| GET | `/api/health/{component}` | 특정 컴포넌트 상태 |
| GET | `/api/health/components` | 모든 컴포넌트 상태 |
| GET | `/api/health/version` | 버전 정보 |

### 7. 모니터링 (`/api/observability/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/observability/dashboard` | 모니터링 대시보드 |
| GET | `/api/observability/lag` | 지연 시간 (Lag) |
| GET | `/api/observability/throughput` | 처리량 |
| GET | `/api/observability/latency` | 응답 시간 |
| GET | `/api/observability/queues` | 대기열 상태 |
| GET | `/api/observability/timeseries/{metric}` | 시계열 메트릭 데이터 |

### 8. 워크플로우 캔버스 (`/api/workflow/*`)

| Method | 경로 | 설명 |
|--------|------|------|
| GET | `/api/workflow/graph` | 전체 파이프라인 그래프 |
| GET | `/api/workflow/nodes/{nodeId}` | 노드 상세 정보 |
| GET | `/api/workflow/stats` | 워크플로우 통계 |

**참고**: RFC-IMPL-015 구현 (파이프라인 시각화)

### 9. 정적 리소스

| 경로 | 설명 |
|------|------|
| `/` | React Admin UI (SPA, `index.html`) |
| `/assets/*` | 정적 리소스 (JS, CSS 등) |
| `/favicon.svg` | 파비콘 |

---

## Runtime API와의 차이

| 항목 | Runtime API | Admin App |
|------|------------|-----------|
| **포트** | 8080 | 8081 |
| **목적** | 비즈니스 API (Ingest, Query) | 모니터링/관리 |
| **Worker** | 포함 (OutboxPollingWorker) | 포함 (동일한 Worker) |
| **Static Files** | 없음 | `/` → `static/admin/` (React SPA) |
| **API 경로** | `/api/v1/*` | `/api/*` (dashboard, contracts, pipeline 등) |
| **실행** | `./gradlew run` | `./gradlew fastAdmin` |
| **독립성** | 완전 독립 | 완전 독립 (같은 DB 공유) |

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
curl http://localhost:8081/api/dashboard

# Outbox 통계
curl http://localhost:8081/api/outbox/stats

# Worker 상태
curl http://localhost:8081/api/worker/status

# Contract 목록
curl http://localhost:8081/api/contracts

# 파이프라인 개요
curl http://localhost:8081/api/pipeline/overview

# 알림 목록
curl http://localhost:8081/api/alerts

# 헬스체크
curl http://localhost:8081/api/health
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

## 주요 기능

### ✅ 구현 완료

- ✅ 대시보드 (Outbox, Worker, DB 통계)
- ✅ Contract 조회 (Read-only)
- ✅ 파이프라인 모니터링 (Raw → Slice → View → Sink)
- ✅ Outbox 관리 (재시도, DLQ 처리)
- ✅ 알림 관리 (규칙, 확인, 무시)
- ✅ 백필 작업 (생성, 시작, 일시정지, 재개, 취소)
- ✅ 헬스체크 (Liveness, Readiness)
- ✅ 모니터링 (지연, 처리량, 대기열)
- ✅ 워크플로우 캔버스 (RFC-IMPL-015)

### ❌ 미구현

- ❌ Contract 생성/수정/삭제 (현재 Read-only)
- ❌ 버전 관리 UI (버전 히스토리, Diff)
- ❌ 마이그레이션 UI (CLI만 존재)
- ❌ 승인 워크플로우
- ❌ 인증/인가 (보안)

## 관련 문서

- [관리자 대시보드 사용 가이드](./admin-dashboard.md)
- [Runtime API](./docs/rfc/rfcimpl009.md)
- [RFC-IMPL-015: Workflow Canvas](../rfc/rfcimpl015-workflow-canvas.md)
- [RFC-IMPL-014: Contract Version Management](../rfc/rfcimpl014-version-runtime-verification.md)
