// Health Types (Backend: SystemHealthDto)
export interface HealthResponse {
  overall: HealthStatus
  components: ComponentHealth[]
  timestamp: string
  version: string
  uptime: number // seconds
}

export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN'

export interface ComponentHealth {
  name: string
  status: HealthStatus
  latencyMs: number
  message?: string | null
  details: Record<string, string>
  checkedAt: string
  error?: string | null
}
