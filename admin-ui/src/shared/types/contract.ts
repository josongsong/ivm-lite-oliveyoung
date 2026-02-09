export interface ContractListResponse {
  contracts: Contract[]
  total: number
}

export interface Contract {
  kind: string
  id: string
  version: string
  status: string
  fileName: string
  content: string
  parsed: Record<string, unknown>
}

export interface ContractStatsResponse {
  total: number
  byKind: Record<string, number>
  byStatus: Record<string, number>
}

// Graph types
export interface GraphNode {
  id: string
  label: string
  kind: string
  status?: string
  entityType?: string
  metadata?: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  /** Alias for source */
  from: string
  /** Alias for target */
  to: string
  label?: string
  kind?: string
}

export interface GraphResponse {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface ImpactAnalysisResponse {
  contract: { kind: string; id: string }
  impactedContracts: Array<{ kind: string; id: string; impactType: string }>
  totalImpacted: number
}
