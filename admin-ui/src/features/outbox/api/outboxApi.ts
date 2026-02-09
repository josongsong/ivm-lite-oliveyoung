/**
 * Outbox API 래퍼 (DIP 준수)
 * - fetchApi/postApi 추상화 계층
 * - 타입 안정성 보장
 * - API 변경 시 이 파일만 수정
 */
import { fetchApi, postApi } from '@/shared/api'
import { OUTBOX_CONFIG } from '@/shared/config'
import type {
  DlqResponse,
  FailedResponse,
  OutboxEntryDto,
  RecentOutboxResponse,
  StaleResponse,
} from '@/shared/types'

const { DEFAULT_LIMIT, BATCH_RETRY_LIMIT, STALE_TIMEOUT_SECONDS } = OUTBOX_CONFIG

export const outboxApi = {
  /** 최근 Outbox 조회 */
  getRecent: (limit = DEFAULT_LIMIT) =>
    fetchApi<RecentOutboxResponse>(`/outbox/recent?limit=${limit}`),

  /** 실패한 Outbox 조회 */
  getFailed: (limit = DEFAULT_LIMIT) =>
    fetchApi<FailedResponse>(`/outbox/failed?limit=${limit}`),

  /** DLQ 조회 */
  getDlq: (limit = DEFAULT_LIMIT) =>
    fetchApi<DlqResponse>(`/outbox/dlq?limit=${limit}`),

  /** Stale 조회 */
  getStale: (timeout = STALE_TIMEOUT_SECONDS) =>
    fetchApi<StaleResponse>(`/outbox/stale?timeout=${timeout}`),

  /** 특정 Outbox 상세 조회 */
  getById: (id: string) =>
    fetchApi<OutboxEntryDto>(`/outbox/${id}`),

  /** DLQ 재처리 */
  replayDlq: (id: string) =>
    postApi<{ success: boolean }>(`/outbox/dlq/${id}/replay`),

  /** 개별 재시도 */
  retry: (id: string) =>
    postApi<{ success: boolean; entry: OutboxEntryDto }>(`/outbox/${id}/retry`),

  /** 일괄 재시도 */
  retryAll: (limit = BATCH_RETRY_LIMIT) =>
    postApi<{ success: boolean; retriedCount: number }>(`/outbox/failed/retry-all?limit=${limit}`),

  /** Stale 복구 */
  releaseStale: (timeout = STALE_TIMEOUT_SECONDS) =>
    postApi<{ released: number }>(`/outbox/stale/release?timeout=${timeout}`),
}
