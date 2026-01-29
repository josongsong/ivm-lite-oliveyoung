// GAP-2: 시간대별 통계 타입

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
