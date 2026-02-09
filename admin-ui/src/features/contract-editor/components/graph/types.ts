/**
 * Impact Graph Types (Phase 4)
 */
import type { Edge, Node } from '@xyflow/react'

export type ImpactLevel = 'changed' | 'affected' | 'normal'
export type FilterMode = 'all' | 'affected'
export type DepthLevel = 1 | 2 | 3 | 'all'

export interface ContractNodeData extends Record<string, unknown> {
  id: string
  kind: string
  impact?: ImpactLevel
  label?: string
}

export interface DependencyEdgeData extends Record<string, unknown> {
  label?: string
  relationship?: string
}

export type ImpactNode = Node<ContractNodeData>
export type ImpactEdge = Edge<DependencyEdgeData>
