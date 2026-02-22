export interface DashboardResponse {
  sinkEvent: SinkEventStats
  worker: WorkerStatus
  database: DatabaseStats
  timestamp: string
}

export interface SinkEventStats {
  total: {
    pending: number
    processing: number
    failed: number
    completed: number
  }
  byStatus: Record<string, number>
  details: SinkEventDetail[]
}

export interface SinkEventDetail {
  status: string
  viewType: string
  count: number
  oldest: string | null
  newest: string | null
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
  sinkEventCount: number
  contractsCount: number
  note: string
}
