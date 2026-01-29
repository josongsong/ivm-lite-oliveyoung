export interface RecentOutboxResponse {
  items: OutboxItem[]
  count: number
}

export interface OutboxItem {
  id: string
  aggregateType: string
  aggregateId: string
  eventType: string
  status: string
  createdAt: string | null
  processedAt: string | null
  retryCount: number
  failureReason?: string
}

export interface DlqResponse {
  items: OutboxEntryDto[]
  count: number
}

export interface OutboxEntryDto {
  id: string
  idempotencyKey: string
  aggregateType: string
  aggregateId: string
  eventType: string
  payload: string
  status: string
  createdAt: string
  processedAt: string | null
  claimedAt: string | null
  claimedBy: string | null
  retryCount: number
  failureReason: string | null
  priority: number | null
  entityVersion: number | null
}

// GAP-1: 실패 작업 재시도 응답 타입
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

export interface StaleOutboxItem {
  id: string
  aggregateType: string
  aggregateId: string
  eventType: string
  claimedAt: string | null
  claimedBy: string | null
  ageSeconds: number
}

export interface StaleResponse {
  items: StaleOutboxItem[]
  count: number
  timeoutSeconds: number
}

export interface FailedOutboxItem {
  id: string
  aggregateType: string
  aggregateId: string
  eventType: string
  createdAt: string | null
  retryCount: number
  failureReason: string | null
}

export interface FailedResponse {
  items: FailedOutboxItem[]
  count: number
}
