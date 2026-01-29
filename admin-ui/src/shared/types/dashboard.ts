export interface DashboardResponse {
  outbox: OutboxStats
  worker: WorkerStatus
  database: DatabaseStats
  timestamp: string
}

export interface OutboxStats {
  total: {
    pending: number
    processing: number
    failed: number
    processed: number
  }
  byStatus: Record<string, number>
  byType: Record<string, number>
  details: OutboxDetail[]
}

export interface OutboxDetail {
  status: string
  aggregateType: string
  count: number
  oldest: string | null
  newest: string | null
  avgLatencySeconds: number | null
}

export interface WorkerStatus {
  running: boolean
  processed: number
  failed: number
  polls: number
  lastPollTime: number | null
}

export interface DatabaseStats {
  rawDataCount: number
  outboxCount: number
  note: string
}
