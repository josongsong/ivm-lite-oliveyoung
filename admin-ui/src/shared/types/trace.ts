// Trace types (AWS X-Ray 연동)

export interface TraceListResult {
  traces: TraceSummary[]
  nextToken?: string | null
  approximateCount: number
}

export interface TraceSummary {
  traceId: string
  duration: number // ms
  startTime: string // ISO8601
  hasError: boolean
  hasFault: boolean
  hasThrottle: boolean
  http?: HttpInfo | null
  annotations: Record<string, string>
  serviceIds: string[]
}

export interface TraceDetail {
  traceId: string
  duration: number // ms
  startTime: string // ISO8601
  endTime?: string | null // ISO8601
  segments: SpanDetail[]
}

export interface SpanDetail {
  spanId: string
  parentId?: string | null
  name: string
  startTime: number // Unix timestamp (seconds)
  endTime: number
  duration: number // ms
  service: string
  type: string
  http?: HttpInfo | null
  annotations?: Record<string, string> | null
  metadata?: Record<string, string> | null
  hasError: boolean
  errorMessage?: string | null
}

export interface HttpInfo {
  method?: string | null
  url?: string | null
  status?: number | null
}

export interface ServiceMapResult {
  startTime: string // ISO8601
  endTime: string // ISO8601
  services: ServiceNode[]
}

export interface ServiceNode {
  name: string
  referenceId: number
  names: string[]
  edges: ServiceEdge[]
}

export interface ServiceEdge {
  referenceId: number
  startTime: string // ISO8601
  endTime: string // ISO8601
  summaryStatistics?: EdgeStatistics | null
}

export interface EdgeStatistics {
  okCount: number
  errorCount: number
  faultCount: number
  throttleCount: number
  totalCount: number
  totalResponseTime: number
}

// Trace 필터 옵션
export interface TraceFilterOptions {
  startTime?: string // ISO8601
  endTime?: string // ISO8601
  serviceName?: string
  limit?: number
  nextToken?: string | null
}
