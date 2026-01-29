export interface PipelineOverviewResponse {
  stages: PipelineStage[]
  rawData: RawDataStats
  slices: SliceStats
  outbox: OutboxPipelineStats
  timestamp: string
}

export interface PipelineStage {
  name: string
  description: string
  count: number
  status: string
  icon: string
}

export interface RawDataStats {
  total: number
  byTenant: Record<string, number>
  bySchema: Record<string, number>
}

export interface SliceStats {
  total: number
  byType: Record<string, number>
}

export interface OutboxPipelineStats {
  pending: number
  processing: number
  shipped: number
  failed: number
}
