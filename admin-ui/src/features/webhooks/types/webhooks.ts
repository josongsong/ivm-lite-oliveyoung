/**
 * Webhook Types
 *
 * 웹훅 관련 TypeScript 타입 정의
 */

export type WebhookEvent =
  | 'RAWDATA_INGESTED'
  | 'SLICE_CREATED'
  | 'SLICE_UPDATED'
  | 'SLICE_DELETED'
  | 'VIEW_ASSEMBLED'
  | 'VIEW_CHANGED'
  | 'SINK_SHIPPED'
  | 'SINK_FAILED'
  | 'ERROR'
  | 'WORKER_STARTED'
  | 'WORKER_STOPPED'
  | 'BACKFILL_COMPLETED'

export type DeliveryStatus =
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'RETRYING'
  | 'CIRCUIT_OPEN'
  | 'RATE_LIMITED'

export interface RetryPolicy {
  maxRetries: number
  initialDelayMs: number
  maxDelayMs: number
  multiplier: number
}

export interface Webhook {
  id: string
  name: string
  url: string
  events: WebhookEvent[]
  filters: Record<string, string>
  headers: Record<string, string>
  payloadTemplate: string | null
  isActive: boolean
  retryPolicy: RetryPolicy
  secretToken: string | null
  createdAt: string
  updatedAt: string
}

export interface WebhookDelivery {
  id: string
  webhookId: string
  eventType: WebhookEvent
  status: DeliveryStatus
  responseStatus: number | null
  latencyMs: number | null
  errorMessage: string | null
  attemptCount: number
  createdAt: string
}

export interface WebhookStats {
  webhooks: {
    total: number
    active: number
    inactive: number
  }
  deliveries: {
    total: number
    success: number
    failed: number
    retrying: number
    successRate: number
    avgLatencyMs: number | null
    today: number
  }
  handler: {
    isRunning: boolean
    eventsReceived: number
    eventsDispatched: number
    eventsDropped: number
  }
}

export interface EventInfo {
  name: WebhookEvent
  description: string
  category: string
}

export interface TestResult {
  success: boolean
  statusCode: number | null
  latencyMs: number | null
  errorMessage: string | null
  deliveryId: string
}

// Request/Response types
export interface CreateWebhookRequest {
  name: string
  url: string
  events: string[]
  filters?: Record<string, string>
  headers?: Record<string, string>
  payloadTemplate?: string
  retryPolicy?: RetryPolicy
  secretToken?: string
}

export interface UpdateWebhookRequest {
  name?: string
  url?: string
  events?: string[]
  filters?: Record<string, string>
  headers?: Record<string, string>
  payloadTemplate?: string
  isActive?: boolean
  retryPolicy?: RetryPolicy
  secretToken?: string
}

export interface WebhookListResponse {
  webhooks: Webhook[]
  total: number
}

export interface DeliveryListResponse {
  deliveries: WebhookDelivery[]
  total: number
}

export interface EventListResponse {
  events: EventInfo[]
}
