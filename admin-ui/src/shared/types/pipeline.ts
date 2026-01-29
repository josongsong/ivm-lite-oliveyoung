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

// Pipeline detail types
export interface RawDataItem {
  tenantId: string
  entityKey: string
  version: number
  schemaId: string
  createdAt: string | null
}

export interface SliceItem {
  tenantId: string
  entityKey: string
  version: number
  sliceType: string
  hash: string
  createdAt: string | null
}

export interface SliceDetailResponse {
  stats: { total: number; byType: Record<string, number> }
  byType: { type: string; count: number; lastCreated: string | null }[]
  recent: SliceItem[]
}

export interface RawDataDetailResponse {
  stats: { total: number; byTenant: Record<string, number>; bySchema: Record<string, number> }
  recent: RawDataItem[]
}

export interface EntityFlowResponse {
  entityKey: string
  rawData: RawDataItem[]
  slices: SliceItem[]
  outbox: {
    id: string
    aggregateType: string
    eventType: string
    status: string
    createdAt: string | null
    processedAt: string | null
  }[]
}
