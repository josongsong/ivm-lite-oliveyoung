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
