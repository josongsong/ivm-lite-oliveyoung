// Backfill Types
export interface BackfillResponse {
  activeJobs: BackfillJob[]
  recentJobs: BackfillJob[]
}

export type BackfillStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PAUSED' | 'CANCELLED'

export interface BackfillJob {
  id: string
  name: string
  status: BackfillStatus
  progress: number // 0-100
  processedCount: number
  totalCount: number
  throughputPerSec: number | null
  etaSeconds: number | null
  errorMessage?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
  durationSeconds?: number
}

export interface CreateBackfillRequest {
  name: string
  scope: BackfillScope
}

export interface BackfillScope {
  type: 'ALL' | 'CONTRACT' | 'ENTITY_KEY' | 'DATE_RANGE'
  contractId?: string
  entityKeys?: string[]
  fromDate?: string
  toDate?: string
}
