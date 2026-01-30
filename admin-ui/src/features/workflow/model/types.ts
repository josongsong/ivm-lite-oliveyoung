import type { Edge, Node } from '@xyflow/react'

// 노드 타입
export type WorkflowNodeType =
  | 'rawdata'
  | 'ruleset'
  | 'slice'
  | 'viewdef'
  | 'view'
  | 'sinkrule'
  | 'sink'

// 노드 상태
export type NodeStatus = 'healthy' | 'warning' | 'error' | 'inactive'

// 노드 통계
export interface NodeStats {
  recordCount: number
  throughput: number
  latencyP99Ms?: number
  errorCount: number
  lastUpdatedAt?: string
}

// 노드 데이터 (Record 호환을 위해 인덱스 시그니처 추가)
export interface WorkflowNodeData {
  label: string
  entityType?: string
  contractId?: string
  status: NodeStatus
  stats?: NodeStats
  metadata: Record<string, unknown>
  [key: string]: unknown
}

// React Flow 노드 타입
export type WorkflowNode = Node<WorkflowNodeData, WorkflowNodeType>

// 엣지 스타일 타입
export type EdgeStyleType = 'DEFAULT' | 'DASHED' | 'ANIMATED' | 'ERROR'

// React Flow 엣지 타입
export interface WorkflowEdge extends Omit<Edge, 'style'> {
  animated?: boolean
  type?: 'smoothstep' | 'bezier' | 'straight' | 'labeled'
  label?: string
  edgeStyle?: EdgeStyleType
}

// API 응답
export interface WorkflowGraphResponse {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  metadata: GraphMetadata
}

export interface GraphMetadata {
  entityTypes: string[]
  totalNodes: number
  totalEdges: number
  healthSummary: {
    healthy: number
    warning: number
    error: number
    inactive: number
  }
  lastUpdatedAt: string
}

// 노드 상세 정보
export interface NodeDetailResponse {
  node: WorkflowNode
  relatedContracts: ContractSummary[]
  upstreamNodes: string[]
  downstreamNodes: string[]
  recentActivity: ActivityItem[]
  metrics: NodeMetrics
}

export interface ContractSummary {
  id: string
  kind: string
  version: string
}

export interface ActivityItem {
  timestamp: string
  action: string
  details: string
}

export interface NodeMetrics {
  avgLatencyMs: number
  p99LatencyMs: number
  errorRate: number
  throughputTrend: number[]
}
