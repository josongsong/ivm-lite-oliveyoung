// Observability Types (Backend: PipelineDashboardDto)
export interface ObservabilityResponse {
  metrics: PipelineMetrics
  lag: LagMetrics
  status: PipelineStatus
  timestamp: string
}

export interface PipelineMetrics {
  e2eLatency: LatencyMetrics
  throughput: ThroughputMetrics
  queueDepths: QueueDepthMetrics
}

export interface LatencyMetrics {
  avgMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  maxMs: number
  sampleCount: number
}

export interface ThroughputMetrics {
  recordsPerSecond: number
  recordsPerMinute: number
  recordsPerHour: number
  recentMinuteCount: number
}

export interface QueueDepthMetrics {
  pending: number
  processing: number
  failed: number
  dlq: number
  stale: number
  totalPending: number
  totalProblematic: number
}

export interface LagMetrics {
  currentLag: number
  estimatedLagSeconds: number | null
  trend: 'RISING' | 'STABLE' | 'FALLING'
  delta: number
  timestamp: string
}

export interface PipelineStatus {
  health: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY'
  issues: string[]
  summary: string
}

// For backwards compatibility
export interface KeyMetrics {
  throughputPerMin: number
  throughputChange: number
  latencyP99Ms: number
  latencyChange: number
  lag: number
  lagTrend: 'rising' | 'stable' | 'falling'
}

export interface ThroughputPoint {
  time: string
  value: number
}

export interface QueueStatus {
  pending: number
  pendingPercent: number
  processing: number
  processingPercent: number
  failed: number
  failedPercent: number
  dlq: number
  dlqPercent: number
}

export interface LatencyPercentiles {
  p50: number
  p90: number
  p99: number
  max: number
}
