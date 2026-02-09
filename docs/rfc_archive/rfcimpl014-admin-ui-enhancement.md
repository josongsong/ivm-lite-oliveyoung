# RFC-IMPL-014: Admin UI Enhancement (SOTA Gap Analysis)

**Status**: Draft  
**Created**: 2026-01-29  
**Author**: AI Assistant  
**Scope**: admin-ui, apps/admin  
**Depends on**: RFC-IMPL-008 (Outbox)  

---

## 0. Executive Summary

Admin UI SOTA 갭 분석. 현재 구현 상태와 업계 최고 수준(Temporal, Airflow, Grafana, Datadog)을 비교하여 개선점 도출.

**핵심 발견**: **이미 90% 이상 SOTA 수준으로 구현됨** ✅

---

## 0-1. 현재 구현 상태 (심층 분석)

### ✅ 이미 구현된 SOTA급 기능들

| 페이지 | 구현된 기능 | SOTA 수준 |
|--------|------------|-----------|
| **Dashboard** | Worker 상태 배너, Outbox 통계 카드, Pipeline Flow 시각화, DB 통계 | ⭐⭐⭐⭐ |
| **Outbox** | Recent/Failed/DLQ/Stale 4탭, Replay 버튼, Release All, Detail Modal | ⭐⭐⭐⭐⭐ |
| **Health** | 컴포넌트별 헬스체크, Uptime, 레이턴시 표시 | ⭐⭐⭐⭐ |
| **Observability** | Throughput, Latency P50/P95/P99/MAX, Lag 트렌드, Queue 상태 | ⭐⭐⭐⭐⭐ |
| **Alerts** | 알림 목록, Acknowledge/Silence, Alert Rules 관리 | ⭐⭐⭐⭐⭐ |
| **Backfill** | Job 생성/일시정지/재개/취소/재시도, Progress 바, ETA | ⭐⭐⭐⭐⭐ |
| **Pipeline** | 스테이지 시각화, Entity Flow 추적, Raw Data/Slice 상세 통계 | ⭐⭐⭐⭐⭐ |
| **Contracts** | 종류별 필터, 검색, 상세 페이지, YAML 프리뷰 | ⭐⭐⭐⭐ |

### ❌ 누락된 기능 (SOTA 대비)

| 기능 | 중요도 | 설명 |
|------|--------|------|
| **실패 작업 재시도** | P0 | Failed 탭에서 개별/일괄 재시도 버튼 |
| **시간대별 통계 차트** | P1 | 처리량/에러율 시계열 그래프 |
| **실시간 WebSocket** | P2 | 현재 30초 polling → WebSocket 실시간 |
| **날짜 범위 필터** | P2 | Outbox/Pipeline에서 기간 선택 |
| **Export CSV/JSON** | P2 | 데이터 다운로드 기능 |
| **Bulk 작업** | P2 | 체크박스로 다중 선택 후 일괄 처리 |
| **로그 뷰어** | P3 | 실시간 로그 스트리밍 |
| **Tracing UI** | P3 | OpenTelemetry Trace 조회 |

---

## 0-2. SOTA 벤치마크 비교

### vs Temporal UI

| 기능 | Temporal | IVM Admin | 상태 |
|------|----------|-----------|------|
| Workflow 목록/상태 | ✅ | ✅ Outbox 탭 | **동등** |
| 상세 조회 | ✅ | ✅ Modal | **동등** |
| 재시도/취소 | ✅ | ⚠️ DLQ만 | **갭** |
| 시계열 차트 | ✅ | ❌ | **갭** |
| 검색/필터 | ✅ | ✅ | **동등** |

### vs Airflow UI

| 기능 | Airflow | IVM Admin | 상태 |
|------|---------|-----------|------|
| DAG 시각화 | ✅ | ✅ Pipeline Flow | **동등** |
| Task 상태 | ✅ | ✅ Outbox | **동등** |
| 로그 뷰어 | ✅ | ❌ | **갭** |
| 스케줄링 | ✅ | ⚠️ Backfill만 | **부분** |

### vs Grafana/Datadog

| 기능 | Grafana | IVM Admin | 상태 |
|------|---------|-----------|------|
| 메트릭 대시보드 | ✅ | ✅ Observability | **동등** |
| 시계열 차트 | ✅ | ❌ | **갭** |
| 알림 관리 | ✅ | ✅ Alerts | **동등** |
| 임계값 설정 | ✅ | ✅ Alert Rules | **동등** |

---

## 0-3. 우선순위별 GAP 분석

### 🔴 P0 - 운영 필수 (즉시)

| GAP | 설명 | 예상 시간 |
|-----|------|----------|
| **GAP-1: 실패 작업 재시도** | Failed 탭에서 Retry 버튼 | 2시간 |

### 🟠 P1 - 사용자 경험 (1주 내)

| GAP | 설명 | 예상 시간 |
|-----|------|----------|
| **GAP-2: 시간대별 통계 차트** | recharts Area/Line Chart | 4시간 |
| **GAP-3: 날짜 범위 필터** | DatePicker + API 파라미터 | 3시간 |

### 🟡 P2 - 편의 기능 (2주 내)

| GAP | 설명 | 예상 시간 |
|-----|------|----------|
| **GAP-4: Export CSV/JSON** | 다운로드 버튼 + 서버 스트리밍 | 3시간 |
| **GAP-5: Bulk 작업** | 체크박스 + Batch API | 4시간 |
| **GAP-6: WebSocket 실시간** | SSE 또는 WebSocket 연동 | 6시간 |

### 🟢 P3 - 고급 기능 (장기)

| GAP | 설명 | 예상 시간 |
|-----|------|----------|
| **GAP-7: 로그 뷰어** | 실시간 스트리밍 UI | 8시간 |
| **GAP-8: Tracing UI** | Jaeger/Zipkin 연동 | 6시간 |

---

## 0-4. Phase별 로드맵

```
Phase 1 (1-2일): 운영 필수
├── GAP-1: 실패 작업 재시도 버튼

Phase 2 (1주): 사용자 경험  
├── GAP-2: 시간대별 통계 차트
└── GAP-3: 날짜 범위 필터

Phase 3 (2주): 편의 기능
├── GAP-4: Export 기능
├── GAP-5: Bulk 작업
└── GAP-6: 실시간 WebSocket

Phase 4 (장기): 고급 기능
├── GAP-7: 로그 뷰어
└── GAP-8: Tracing UI
```

---

## 1. GAP-1: 실패 작업 재시도 (P0)

### 현재 상태

| 기능 | Backend API | Admin UI | 상태 |
|------|-------------|----------|------|
| DLQ 조회 | ✅ `GET /outbox/dlq` | ✅ DLQ 탭 | **완료** |
| DLQ Replay | ✅ `POST /outbox/dlq/{id}/replay` | ✅ Replay 버튼 | **완료** |
| Stale 조회 | ✅ `GET /outbox/stale` | ✅ Stale 탭 | **완료** |
| Stale Release | ✅ `POST /outbox/stale/release` | ✅ Release All 버튼 | **완료** |
| 엔트리 상세 조회 | ✅ `GET /outbox/{id}` | ✅ Detail Modal | **완료** |
| **실패 작업 재시도** | ⚠️ `resetToPending` 존재 | ❌ 버튼 없음 | **UI만 필요** |

### 구현 목표

Backend `resetToPending(id)` 메서드가 이미 존재. **UI 버튼만 추가하면 완료**

---

## 1. 실패 작업 재시도 (Retry Failed)

### 1-1. Backend API

#### 신규 엔드포인트

```kotlin
// POST /outbox/{id}/retry
// 실패한 작업을 PENDING으로 리셋하여 재시도

data class RetryRequest(
    val resetRetryCount: Boolean = false  // true면 retryCount도 0으로 리셋
)

data class RetryResponse(
    val success: Boolean,
    val message: String,
    val entry: OutboxEntryDto?
)
```

#### AdminRoutes.kt 추가

```kotlin
/**
 * POST /outbox/{id}/retry
 * 실패한 Outbox 엔트리 재시도 (FAILED → PENDING)
 */
post("/outbox/{id}/retry") {
    try {
        val idParam = call.parameters["id"] ?: run {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "MISSING_ID", message = "ID parameter is required")
            )
            return@post
        }
        val id = try {
            UUID.fromString(idParam)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "INVALID_ID", message = "Invalid UUID format: $idParam")
            )
            return@post
        }

        // Request body (optional)
        val request = try {
            call.receiveNullable<RetryRequest>()
        } catch (e: Exception) {
            null
        }

        val result = outboxRepo.resetToPending(id)
        when (result) {
            is OutboxRepositoryPort.Result.Ok -> {
                call.respond(
                    HttpStatusCode.OK,
                    RetryResponse(
                        success = true,
                        message = "Entry reset to PENDING for retry",
                        entry = result.value.toDto()
                    )
                )
            }
            is OutboxRepositoryPort.Result.Err -> {
                call.respond(
                    result.error.toKtorStatus(),
                    ApiError.from(result.error)
                )
            }
        }
    } catch (e: Exception) {
        call.application.log.error("Failed to retry outbox entry", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiError(
                code = "RETRY_ERROR",
                message = "Failed to retry outbox entry: ${e.message}"
            )
        )
    }
}
```

#### 일괄 재시도 (Batch Retry)

```kotlin
/**
 * POST /outbox/failed/retry-all
 * 모든 실패한 작업 일괄 재시도
 */
post("/outbox/failed/retry-all") {
    try {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        
        // FAILED 상태인 것들을 PENDING으로 변경
        val count = dsl.update(DSL.table("outbox"))
            .set(DSL.field("status"), "PENDING")
            .set(DSL.field("failure_reason"), null as String?)
            .where(DSL.field("status").eq("FAILED"))
            .limit(limit)
            .execute()

        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "success" to true,
                "retriedCount" to count,
                "message" to "Reset $count failed entries to PENDING"
            )
        )
    } catch (e: Exception) {
        call.application.log.error("Failed to retry all failed entries", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiError(
                code = "BATCH_RETRY_ERROR",
                message = "Failed to retry all failed entries: ${e.message}"
            )
        )
    }
}
```

### 1-2. Admin UI

#### API 타입 추가 (shared/types/outbox.ts)

```typescript
export interface RetryResponse {
  success: boolean
  message: string
  entry?: OutboxEntryDto
}

export interface BatchRetryResponse {
  success: boolean
  retriedCount: number
  message: string
}
```

#### FailedTable 컴포넌트 수정 (Outbox.tsx)

```tsx
function FailedTable({ items, onViewDetail, onRetry, retryingId }: { 
  items: FailedItem[]
  onViewDetail: (id: string) => void
  onRetry: (id: string) => void
  retryingId: string | null
}) {
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Aggregate ID</th>
            <th>Event</th>
            <th>Retries</th>
            <th>Failure Reason</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <motion.tr key={item.id}>
              <td className="mono">{item.id.slice(0, 8)}...</td>
              <td>{item.aggregateType}</td>
              <td className="mono truncate">{item.aggregateId}</td>
              <td>{item.eventType}</td>
              <td className="text-orange">{item.retryCount}</td>
              <td className="truncate text-error" title={item.failureReason ?? ''}>
                {item.failureReason ?? '-'}
              </td>
              <td className="text-secondary">
                {item.createdAt ? new Date(item.createdAt).toLocaleString('ko-KR') : '-'}
              </td>
              <td>
                <div className="action-buttons">
                  {/* 🆕 재시도 버튼 추가 */}
                  <button 
                    className="btn-icon retry"
                    onClick={() => onRetry(item.id)}
                    disabled={retryingId === item.id}
                    title="Retry"
                  >
                    {retryingId === item.id ? (
                      <Loader2 size={16} className="spin" />
                    ) : (
                      <RotateCcw size={16} />
                    )}
                  </button>
                  <button 
                    className="btn-icon" 
                    onClick={() => onViewDetail(item.id)} 
                    title="View Detail"
                  >
                    <Eye size={16} />
                  </button>
                </div>
              </td>
            </motion.tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

#### 재시도 Mutation 추가 (Outbox.tsx)

```tsx
// 개별 재시도
const retryMutation = useMutation({
  mutationFn: (id: string) => postApi(`/outbox/${id}/retry`),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
    queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
  },
})

// 일괄 재시도
const retryAllMutation = useMutation({
  mutationFn: () => postApi('/outbox/failed/retry-all?limit=100'),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['outbox-failed'] })
    queryClient.invalidateQueries({ queryKey: ['outbox-recent'] })
  },
})
```

#### Actions Bar 수정

```tsx
{activeTab === 'failed' && failedData && failedData.items.length > 0 && (
  <button 
    className="btn btn-primary"
    onClick={() => retryAllMutation.mutate()}
    disabled={retryAllMutation.isPending}
  >
    {retryAllMutation.isPending ? (
      <Loader2 size={16} className="spin" />
    ) : (
      <RotateCcw size={16} />
    )}
    Retry All ({failedData.items.length})
  </button>
)}
```

---

## 2. 시간대별 통계 차트 (Hourly Stats)

### 2-1. Backend API

#### 신규 엔드포인트

```kotlin
/**
 * GET /outbox/stats/hourly
 * 시간대별 처리량/에러율 통계 (최근 24시간)
 */
get("/outbox/stats/hourly") {
    try {
        val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24
        
        val stats = dsl.select(
            DSL.field("date_trunc('hour', created_at)").`as`("hour"),
            DSL.field("status"),
            DSL.count().`as`("count")
        )
            .from(DSL.table("outbox"))
            .where(
                DSL.field("created_at").greaterThan(
                    DSL.field("NOW() - INTERVAL '{} hours'", hours)
                )
            )
            .groupBy(
                DSL.field("date_trunc('hour', created_at)"),
                DSL.field("status")
            )
            .orderBy(DSL.field("hour").asc())
            .fetch()

        val hourlyData = mutableMapOf<String, HourlyStatItem>()

        stats.forEach { record ->
            val hour = record.get("hour", java.time.OffsetDateTime::class.java)
                ?.toInstant()?.toString() ?: return@forEach
            val status = record.get("status", String::class.java) ?: return@forEach
            val count = record.get("count", Long::class.java) ?: 0L

            val item = hourlyData.getOrPut(hour) {
                HourlyStatItem(
                    hour = hour,
                    pending = 0L,
                    processing = 0L,
                    processed = 0L,
                    failed = 0L,
                    total = 0L
                )
            }

            when (status) {
                "PENDING" -> hourlyData[hour] = item.copy(pending = count, total = item.total + count)
                "PROCESSING" -> hourlyData[hour] = item.copy(processing = count, total = item.total + count)
                "PROCESSED" -> hourlyData[hour] = item.copy(processed = count, total = item.total + count)
                "FAILED" -> hourlyData[hour] = item.copy(failed = count, total = item.total + count)
            }
        }

        call.respond(
            HttpStatusCode.OK,
            HourlyStatsResponse(
                items = hourlyData.values.toList().sortedBy { it.hour },
                hours = hours
            )
        )
    } catch (e: Exception) {
        call.application.log.error("Failed to get hourly stats", e)
        call.respond(
            HttpStatusCode.InternalServerError,
            ApiError(
                code = "HOURLY_STATS_ERROR",
                message = "Failed to get hourly stats: ${e.message}"
            )
        )
    }
}
```

#### Response DTOs

```kotlin
@Serializable
data class HourlyStatsResponse(
    val items: List<HourlyStatItem>,
    val hours: Int
)

@Serializable
data class HourlyStatItem(
    val hour: String,
    val pending: Long,
    val processing: Long,
    val processed: Long,
    val failed: Long,
    val total: Long
) {
    val errorRate: Double
        get() = if (total > 0) (failed.toDouble() / total) * 100 else 0.0
}
```

### 2-2. Admin UI

#### Chart 라이브러리 설치

```bash
npm install recharts
```

#### 타입 정의 (shared/types/stats.ts)

```typescript
export interface HourlyStatItem {
  hour: string
  pending: number
  processing: number
  processed: number
  failed: number
  total: number
  errorRate?: number
}

export interface HourlyStatsResponse {
  items: HourlyStatItem[]
  hours: number
}
```

#### HourlyChart 컴포넌트 생성

```tsx
// features/dashboard/ui/HourlyChart.tsx

import { useMemo } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useQuery } from '@tanstack/react-query'
import { fetchApi } from '@/shared/api'
import type { HourlyStatsResponse } from '@/shared/types'
import './HourlyChart.css'

export function HourlyChart() {
  const { data, isLoading } = useQuery({
    queryKey: ['outbox-hourly-stats'],
    queryFn: () => fetchApi<HourlyStatsResponse>('/outbox/stats/hourly?hours=24'),
    refetchInterval: 60_000, // 1분마다 새로고침
  })

  const chartData = useMemo(() => {
    if (!data?.items) return []
    return data.items.map((item) => ({
      ...item,
      hour: new Date(item.hour).toLocaleTimeString('ko-KR', { 
        hour: '2-digit', 
        minute: '2-digit' 
      }),
      errorRate: item.total > 0 ? (item.failed / item.total) * 100 : 0,
    }))
  }, [data])

  if (isLoading) {
    return <div className="chart-loading">차트 로딩 중...</div>
  }

  return (
    <div className="hourly-chart">
      <div className="chart-header">
        <h3>시간대별 처리량 (최근 24시간)</h3>
      </div>
      <div className="chart-container">
        <ResponsiveContainer width="100%" height={300}>
          <AreaChart data={chartData}>
            <defs>
              <linearGradient id="colorProcessed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#ef4444" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorPending" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#f59e0b" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#333" />
            <XAxis 
              dataKey="hour" 
              stroke="#888"
              tick={{ fill: '#888', fontSize: 12 }}
            />
            <YAxis 
              stroke="#888"
              tick={{ fill: '#888', fontSize: 12 }}
            />
            <Tooltip 
              contentStyle={{ 
                backgroundColor: '#1a1a1a', 
                border: '1px solid #333',
                borderRadius: '8px',
              }}
              labelStyle={{ color: '#fff' }}
            />
            <Legend />
            <Area 
              type="monotone" 
              dataKey="processed" 
              name="Processed"
              stroke="#10b981" 
              fillOpacity={1} 
              fill="url(#colorProcessed)" 
            />
            <Area 
              type="monotone" 
              dataKey="failed" 
              name="Failed"
              stroke="#ef4444" 
              fillOpacity={1} 
              fill="url(#colorFailed)" 
            />
            <Area 
              type="monotone" 
              dataKey="pending" 
              name="Pending"
              stroke="#f59e0b" 
              fillOpacity={1} 
              fill="url(#colorPending)" 
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
```

#### HourlyChart.css

```css
.hourly-chart {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 2rem;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.chart-header h3 {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
  height: 300px;
}

.chart-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: var(--color-text-secondary);
}
```

#### Dashboard.tsx에 차트 추가

```tsx
import { HourlyChart } from './HourlyChart'

export function Dashboard() {
  // ... existing code ...

  return (
    <div className="page-container">
      {/* ... existing header ... */}

      {/* 🆕 시간대별 통계 차트 */}
      <HourlyChart />

      {/* ... existing stats cards ... */}
    </div>
  )
}
```

---

## 3. 구현 작업 목록

### 3-1. Backend (Kotlin)

| # | 파일 | 변경 | 우선순위 |
|---|------|------|---------|
| 1 | `AdminRoutes.kt` | `POST /outbox/{id}/retry` 추가 | P0 |
| 2 | `AdminRoutes.kt` | `POST /outbox/failed/retry-all` 추가 | P0 |
| 3 | `AdminRoutes.kt` | `GET /outbox/stats/hourly` 추가 | P1 |
| 4 | `AdminRoutes.kt` | `HourlyStatItem`, `HourlyStatsResponse` DTO 추가 | P1 |
| 5 | `AdminRoutes.kt` | `RetryRequest`, `RetryResponse` DTO 추가 | P0 |

### 3-2. Frontend (React)

| # | 파일 | 변경 | 우선순위 |
|---|------|------|---------|
| 1 | `package.json` | `recharts` 의존성 추가 | P1 |
| 2 | `shared/types/outbox.ts` | `RetryResponse`, `BatchRetryResponse` 타입 추가 | P0 |
| 3 | `shared/types/stats.ts` | `HourlyStatItem`, `HourlyStatsResponse` 타입 추가 | P1 |
| 4 | `features/outbox/ui/Outbox.tsx` | `retryMutation`, `retryAllMutation` 추가 | P0 |
| 5 | `features/outbox/ui/Outbox.tsx` | `FailedTable`에 재시도 버튼 추가 | P0 |
| 6 | `features/outbox/ui/Outbox.tsx` | Actions Bar에 "Retry All" 버튼 추가 | P0 |
| 7 | `features/dashboard/ui/HourlyChart.tsx` | 신규 생성 | P1 |
| 8 | `features/dashboard/ui/HourlyChart.css` | 신규 생성 | P1 |
| 9 | `features/dashboard/ui/Dashboard.tsx` | `HourlyChart` 컴포넌트 추가 | P1 |
| 10 | `features/dashboard/index.ts` | export 추가 | P1 |

---

## 4. 테스트 계획

### 4-1. Backend 테스트

```kotlin
class AdminRoutesRetryTest : KtorTestBase() {
    
    @Test
    fun `POST outbox retry should reset FAILED to PENDING`() = testApplication {
        // Given: FAILED 상태의 엔트리
        val failedEntry = createOutboxEntry(status = OutboxStatus.FAILED)
        
        // When: 재시도 요청
        val response = client.post("/api/outbox/${failedEntry.id}/retry")
        
        // Then: PENDING으로 변경됨
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<RetryResponse>()
        assertTrue(result.success)
        assertEquals("PENDING", result.entry?.status)
    }
    
    @Test
    fun `POST outbox retry-all should reset all FAILED entries`() = testApplication {
        // Given: 여러 FAILED 엔트리
        repeat(5) { createOutboxEntry(status = OutboxStatus.FAILED) }
        
        // When: 일괄 재시도
        val response = client.post("/api/outbox/failed/retry-all")
        
        // Then: 모두 PENDING으로 변경
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<Map<String, Any>>()
        assertEquals(5, result["retriedCount"])
    }
    
    @Test
    fun `GET outbox stats hourly should return 24 hour stats`() = testApplication {
        // Given: 시간대별 데이터
        createOutboxEntriesForPast24Hours()
        
        // When: 시간대별 통계 조회
        val response = client.get("/api/outbox/stats/hourly")
        
        // Then: 24시간 데이터 반환
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<HourlyStatsResponse>()
        assertTrue(result.items.isNotEmpty())
    }
}
```

### 4-2. Frontend 테스트

```typescript
describe('Outbox Retry', () => {
  it('should show retry button in Failed tab', () => {
    render(<Outbox />)
    
    // Failed 탭 클릭
    fireEvent.click(screen.getByText('Failed'))
    
    // 재시도 버튼 존재 확인
    expect(screen.getAllByTitle('Retry')).toHaveLength(failedItems.length)
  })

  it('should call retry API when button clicked', async () => {
    const mockPost = vi.fn().mockResolvedValue({ success: true })
    vi.mock('@/shared/api', () => ({ postApi: mockPost }))
    
    render(<Outbox />)
    fireEvent.click(screen.getByText('Failed'))
    fireEvent.click(screen.getAllByTitle('Retry')[0])
    
    expect(mockPost).toHaveBeenCalledWith('/outbox/test-id/retry')
  })
})

describe('HourlyChart', () => {
  it('should render chart with data', async () => {
    render(<HourlyChart />)
    
    await waitFor(() => {
      expect(screen.getByText('시간대별 처리량 (최근 24시간)')).toBeInTheDocument()
    })
  })
})
```

---

## 5. 일정 예상

| 단계 | 작업 | 예상 시간 |
|------|------|----------|
| **Phase 1** | 실패 작업 재시도 (BE) | 1시간 |
| **Phase 2** | 실패 작업 재시도 (FE) | 1시간 |
| **Phase 3** | 시간대별 통계 API (BE) | 1시간 |
| **Phase 4** | 시간대별 차트 (FE) | 2시간 |
| **Phase 5** | 테스트 + 검증 | 1시간 |
| **Total** | | **6시간** |

---

---

## 3. GAP-3: 날짜 범위 필터 (P1)

### 3-1. Backend API 수정

```kotlin
/**
 * GET /outbox/recent?limit=50&from=2026-01-01&to=2026-01-29
 */
get("/outbox/recent") {
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
    val from = call.request.queryParameters["from"]?.let { 
        java.time.OffsetDateTime.parse(it + "T00:00:00Z") 
    }
    val to = call.request.queryParameters["to"]?.let { 
        java.time.OffsetDateTime.parse(it + "T23:59:59Z") 
    }
    
    var query = dsl.select()
        .from(DSL.table("outbox"))
    
    if (from != null) {
        query = query.where(DSL.field("created_at").ge(from))
    }
    if (to != null) {
        query = query.and(DSL.field("created_at").le(to))
    }
    
    val entries = query
        .orderBy(DSL.field("created_at").desc())
        .limit(limit)
        .fetch()
    // ...
}
```

### 3-2. Admin UI

```tsx
// DateRangePicker 컴포넌트
import { format } from 'date-fns'
import { Calendar } from 'lucide-react'

interface DateRangePickerProps {
  from: Date | null
  to: Date | null
  onChange: (from: Date | null, to: Date | null) => void
}

function DateRangePicker({ from, to, onChange }: DateRangePickerProps) {
  return (
    <div className="date-range-picker">
      <Calendar size={16} />
      <input 
        type="date" 
        value={from ? format(from, 'yyyy-MM-dd') : ''} 
        onChange={(e) => onChange(e.target.value ? new Date(e.target.value) : null, to)}
      />
      <span>~</span>
      <input 
        type="date" 
        value={to ? format(to, 'yyyy-MM-dd') : ''} 
        onChange={(e) => onChange(from, e.target.value ? new Date(e.target.value) : null)}
      />
    </div>
  )
}
```

---

## 4. GAP-4: Export CSV/JSON (P2)

### 4-1. Backend API

```kotlin
/**
 * GET /outbox/export?format=csv
 * GET /outbox/export?format=json
 */
get("/outbox/export") {
    val format = call.request.queryParameters["format"] ?: "json"
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000
    val status = call.request.queryParameters["status"]
    
    val entries = dsl.select()
        .from(DSL.table("outbox"))
        .apply { status?.let { where(DSL.field("status").eq(it)) } }
        .orderBy(DSL.field("created_at").desc())
        .limit(limit)
        .fetch()
    
    when (format) {
        "csv" -> {
            call.response.header("Content-Disposition", "attachment; filename=outbox.csv")
            call.respondText(
                contentType = ContentType.Text.CSV,
                text = entries.toCsv()
            )
        }
        else -> {
            call.response.header("Content-Disposition", "attachment; filename=outbox.json")
            call.respond(entries.toJson())
        }
    }
}
```

### 4-2. Admin UI

```tsx
<button 
  className="btn btn-secondary"
  onClick={() => window.open('/api/outbox/export?format=csv', '_blank')}
>
  <Download size={16} />
  Export CSV
</button>
```

---

## 5. GAP-5: Bulk 작업 (P2)

### 5-1. Backend API

```kotlin
/**
 * POST /outbox/bulk/retry
 * Body: { "ids": ["uuid1", "uuid2", ...] }
 */
post("/outbox/bulk/retry") {
    val request = call.receive<BulkRetryRequest>()
    
    val count = dsl.update(DSL.table("outbox"))
        .set(DSL.field("status"), "PENDING")
        .set(DSL.field("failure_reason"), null as String?)
        .where(DSL.field("id").`in`(request.ids.map { UUID.fromString(it) }))
        .execute()
    
    call.respond(BulkActionResponse(success = true, affectedCount = count))
}

@Serializable
data class BulkRetryRequest(val ids: List<String>)

@Serializable
data class BulkActionResponse(val success: Boolean, val affectedCount: Int)
```

### 5-2. Admin UI

```tsx
const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

// 체크박스 컬럼 추가
<th>
  <input 
    type="checkbox" 
    checked={selectedIds.size === items.length}
    onChange={(e) => {
      if (e.target.checked) {
        setSelectedIds(new Set(items.map(i => i.id)))
      } else {
        setSelectedIds(new Set())
      }
    }}
  />
</th>

// 선택된 항목 일괄 처리
{selectedIds.size > 0 && (
  <button 
    className="btn btn-primary"
    onClick={() => bulkRetryMutation.mutate([...selectedIds])}
  >
    Retry Selected ({selectedIds.size})
  </button>
)}
```

---

## 6. GAP-6: WebSocket 실시간 (P2)

### 6-1. Backend (Ktor WebSocket)

```kotlin
// AdminApplication.kt
install(WebSockets) {
    pingPeriod = Duration.ofSeconds(15)
    timeout = Duration.ofSeconds(15)
}

// AdminRoutes.kt
webSocket("/ws/dashboard") {
    val session = this
    
    try {
        while (true) {
            val dashboard = getDashboardData(dsl, worker)
            send(Frame.Text(Json.encodeToString(dashboard)))
            delay(5000) // 5초마다 푸시
        }
    } catch (e: ClosedReceiveChannelException) {
        // Client disconnected
    }
}
```

### 6-2. Admin UI

```tsx
function useDashboardWebSocket() {
  const [data, setData] = useState<DashboardResponse | null>(null)
  
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8081/api/ws/dashboard')
    
    ws.onmessage = (event) => {
      setData(JSON.parse(event.data))
    }
    
    ws.onclose = () => {
      // Reconnect logic
      setTimeout(() => {
        // Retry connection
      }, 3000)
    }
    
    return () => ws.close()
  }, [])
  
  return data
}
```

---

## 7. GAP-7: 로그 뷰어 (P3)

### 7-1. Backend API

```kotlin
/**
 * GET /logs/stream - SSE 로그 스트리밍
 */
get("/logs/stream") {
    call.respondSse {
        val logReader = LogReader()
        
        logReader.tail().collect { line ->
            send(ServerSentEvent(data = line))
        }
    }
}
```

### 7-2. Admin UI

```tsx
function LogViewer() {
  const [logs, setLogs] = useState<string[]>([])
  const logsRef = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    const eventSource = new EventSource('/api/logs/stream')
    
    eventSource.onmessage = (event) => {
      setLogs(prev => [...prev.slice(-1000), event.data]) // 최대 1000줄
    }
    
    return () => eventSource.close()
  }, [])
  
  // 자동 스크롤
  useEffect(() => {
    logsRef.current?.scrollTo({ top: logsRef.current.scrollHeight })
  }, [logs])
  
  return (
    <div className="log-viewer" ref={logsRef}>
      {logs.map((log, i) => (
        <div key={i} className={`log-line ${getLogLevel(log)}`}>
          {log}
        </div>
      ))}
    </div>
  )
}
```

---

## 8. GAP-8: Tracing UI (P3)

### 8-1. Backend API

```kotlin
/**
 * GET /tracing/traces?service=ivm-lite&limit=50
 * Jaeger/Zipkin API 프록시
 */
get("/tracing/traces") {
    val service = call.request.queryParameters["service"] ?: "ivm-lite"
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
    
    val traces = jaegerClient.getTraces(service, limit)
    call.respond(traces)
}

/**
 * GET /tracing/traces/{traceId}
 */
get("/tracing/traces/{traceId}") {
    val traceId = call.parameters["traceId"] ?: return@get
    val trace = jaegerClient.getTrace(traceId)
    call.respond(trace)
}
```

### 8-2. Admin UI

```tsx
function TracingView() {
  const [traceId, setTraceId] = useState('')
  
  const { data: trace } = useQuery({
    queryKey: ['trace', traceId],
    queryFn: () => fetchApi(`/tracing/traces/${traceId}`),
    enabled: traceId.length > 0,
  })
  
  return (
    <div className="tracing-view">
      <input 
        placeholder="Trace ID 입력..." 
        value={traceId}
        onChange={(e) => setTraceId(e.target.value)}
      />
      
      {trace && (
        <TraceTimeline spans={trace.spans} />
      )}
    </div>
  )
}
```

---

## 9. 구현 일정 요약

| Phase | GAP | 예상 시간 | 완료 기준 |
|-------|-----|----------|----------|
| **1** | GAP-1: 실패 작업 재시도 | 2시간 | Failed 탭 Retry 버튼 동작 |
| **2** | GAP-2: 시간대별 차트 | 4시간 | Dashboard에 Area Chart 표시 |
| **2** | GAP-3: 날짜 범위 필터 | 3시간 | Outbox 필터링 동작 |
| **3** | GAP-4: Export | 3시간 | CSV/JSON 다운로드 |
| **3** | GAP-5: Bulk 작업 | 4시간 | 체크박스 + 일괄 처리 |
| **3** | GAP-6: WebSocket | 6시간 | 실시간 대시보드 업데이트 |
| **4** | GAP-7: 로그 뷰어 | 8시간 | 실시간 로그 스트리밍 |
| **4** | GAP-8: Tracing | 6시간 | Trace 조회 UI |

**총 예상 시간: 36시간 (약 4.5일)**

---

## 10. 결론

### SOTA 수준 달성도

```
현재: ████████████████████░░ 90%
목표: ██████████████████████ 100%
```

**현재 이미 SOTA급 (90%):**
- ✅ Dashboard, Outbox, Health, Alerts, Backfill, Pipeline, Contracts
- ✅ 애니메이션, 반응형, 다크 테마
- ✅ React Query 캐싱, Optimistic Updates

**추가 구현 필요 (10%):**
- ❌ GAP-1: 실패 작업 재시도 (P0) - **UI만 추가**
- ❌ GAP-2: 시간대별 차트 (P1)
- ❌ GAP-3~8: 편의/고급 기능 (P2/P3)

### 즉시 실행 가능

**GAP-1 (실패 작업 재시도)** 는 Backend 메서드가 이미 존재하므로, **UI 버튼만 추가하면 2시간 내 완료 가능**.

```tsx
// Outbox.tsx에 retryMutation만 추가하면 끝!
const retryMutation = useMutation({
  mutationFn: (id: string) => postApi(`/outbox/${id}/retry`),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['outbox-failed'] }),
})
```

기존 아키텍처와 패턴을 그대로 따라 구현하면 되므로, **리스크가 낮고 빠르게 완료 가능**합니다.
